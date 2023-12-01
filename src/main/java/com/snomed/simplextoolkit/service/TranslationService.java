package com.snomed.simplextoolkit.service;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Lists;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.LanguageCode;
import com.snomed.simplextoolkit.service.job.ChangeMonitor;
import com.snomed.simplextoolkit.service.job.ChangeSummary;
import com.snomed.simplextoolkit.util.TimerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.snomed.simplextoolkit.client.domain.Description.CaseSignificance.CASE_INSENSITIVE;
import static com.snomed.simplextoolkit.client.domain.Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
import static com.snomed.simplextoolkit.client.domain.Description.Type.SYNONYM;
import static java.lang.Long.parseLong;
import static java.lang.String.format;

@Service
public class TranslationService {

	public static final String NON_BREAKING_SPACE_CHARACTER = " ";

	private final List<LanguageCode> languageCodes = new ArrayList<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() throws ServiceException {
		String languageCodesFilePath = "/language_codes_iso-639-1.txt";
		InputStream inputStream = getClass().getResourceAsStream(languageCodesFilePath);
		if (inputStream == null) {
			throw new ServiceException(format("Language codes file '%s' missing within application.", languageCodesFilePath));
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isEmpty()) {
					String[] split = line.split("\t");
					if (split.length == 2) {
						languageCodes.add(new LanguageCode(split[0], split[1]));
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<LanguageCode> getLanguageCodes() {
		return languageCodes;
	}

	public List<ConceptMini> listTranslations(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		TimerUtil timer = new TimerUtil("Load translations", Level.INFO, 2);

		List<ConceptMini> translationRefsets = snowstormClient.getRefsets("<" + Concepts.LANG_REFSET, codeSystem);
		timer.checkpoint("ECL for lang refsets");

		for (ConceptMini translationRefset : translationRefsets) {
			Page<RefsetMember> firstMember = snowstormClient.getRefsetMembers(translationRefset.getConceptId(), codeSystem, true, 0, 1);
			timer.checkpoint(format("Load one lang refset member for %s.", translationRefset.getIdAndFsnTerm()));

			if (!firstMember.getItems().isEmpty()) {
				RefsetMember member = firstMember.getItems().iterator().next();
				ReferencedComponent referencedComponent = member.getReferencedComponent();
				String lang = referencedComponent.getLang();
				if (lang != null) {
					translationRefset.addExtraField("lang", lang);
				}
			}
		}
		return translationRefsets;
	}

	public ChangeSummary uploadTranslationAsWeblateCSV(String languageRefsetId, String languageCode, CodeSystem codeSystem, InputStream inputStream,
			boolean translationTermsUseTitleCase, SnowstormClient snowstormClient, ProgressMonitor progressMonitor) throws ServiceException {

		try (CSVOutputChangeMonitor changeMonitor = getCsvOutputChangeMonitor()) {
			return doUploadTranslation(() -> readTranslationsFromWeblateCSV(inputStream, languageCode, languageRefsetId),
					languageRefsetId, translationTermsUseTitleCase, codeSystem, snowstormClient, progressMonitor, changeMonitor);
		}
	}

	public ChangeSummary uploadTranslationAsRefsetToolArchive(String languageRefsetId, CodeSystem codeSystem, InputStream inputStream,
			SnowstormClient snowstormClient, ProgressMonitor progressMonitor) throws ServiceException {

		try (CSVOutputChangeMonitor changeMonitor = getCsvOutputChangeMonitor()) {
			return doUploadTranslation(() -> new RefsetToolTranslationZipReader(inputStream, languageRefsetId).readUpload(),
					languageRefsetId, true, codeSystem, snowstormClient, progressMonitor, changeMonitor);
		}
	}

	private ChangeSummary doUploadTranslation(TranslationUploadProvider uploadProvider, String languageRefsetId,
			boolean translationTermsUseTitleCase, CodeSystem codeSystem, SnowstormClient snowstormClient,
			ProgressMonitor progressMonitor, ChangeMonitor changeMonitor) throws ServiceException {

		logger.info("Reading translation file..");
		Map<Long, List<Description>> conceptDescriptions = uploadProvider.readUpload();

		// Replace bad characters
		for (List<Description> conceptDescriptionList : conceptDescriptions.values()) {
			for (Description description : conceptDescriptionList) {
				description.setTerm(description.getTerm().replace(NON_BREAKING_SPACE_CHARACTER, " "));
			}
		}

		logger.info("Read translation terms for {} concepts", conceptDescriptions.size());
		progressMonitor.setRecordsTotal(conceptDescriptions.size());

		if (conceptDescriptions.isEmpty()) {
			// No change
			int activeRefsetMembers = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
			return new ChangeSummary(0, 0, 0, activeRefsetMembers);
		}

		// Grab language code from first uploaded description
		String languageCode = conceptDescriptions.values().iterator().next().get(0).getLang();
		int processed = 0;
		int batchSize = 500;
		ChangeSummary changeSummary = new ChangeSummary();
		for (List<Long> conceptIdBatch : Lists.partition(new ArrayList<>(conceptDescriptions.keySet()), batchSize)) {
			if (processed > 0 && processed % 1_000 == 0) {
				logger.info("Processed {} / {}", processed, conceptDescriptions.size());
			}
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
			List<Concept> conceptsToUpdate = new ArrayList<>();
			for (Concept concept : concepts) {
				List<Description> uploadedDescriptions = conceptDescriptions.get(parseLong(concept.getConceptId()));
				boolean anyChange = updateConceptDescriptions(concept.getConceptId(), concept.getDescriptions(), uploadedDescriptions,
						languageCode, languageRefsetId, translationTermsUseTitleCase,
						changeMonitor, changeSummary);

				if (anyChange) {
					conceptsToUpdate.add(concept);
				}
			}
			if (!conceptsToUpdate.isEmpty()) {
				logger.info("Updating {} concepts on {}", conceptsToUpdate.size(), codeSystem.getWorkingBranchPath());
				snowstormClient.createUpdateBrowserFormatConcepts(conceptsToUpdate, codeSystem);
			}
			processed += conceptIdBatch.size();
			progressMonitor.setRecordsProcessed(processed);
		}

		int newActiveCount = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
		changeSummary.setNewTotal(newActiveCount);
		logger.info("translation upload complete on {}: {}", codeSystem.getWorkingBranchPath(), changeSummary);
		return changeSummary;
	}

	public boolean updateConceptDescriptions(String conceptId, List<Description> existingDescriptions, List<Description> uploadedDescriptions,
			String languageCode, String languageRefsetId, boolean translationTermsUseTitleCase,
			ChangeMonitor changeMonitor, ChangeSummary changeSummary) throws ServiceException {

		boolean anyChange = false;
		existingDescriptions.sort(Comparator.comparing(Description::isActive).reversed());

		// Remove any active descriptions in snowstorm with a matching concept, language and lang refset if the term is not in the latest CSV
		List<Description> toRemove = new ArrayList<>();
		for (Description snowstormDescription : existingDescriptions) {
			if (snowstormDescription.getLang().equals(languageCode)
					&& snowstormDescription.isActive()
					&& snowstormDescription.getAcceptabilityMap().containsKey(languageRefsetId)) {

				if (uploadedDescriptions.stream().noneMatch(uploadedDescription -> uploadedDescription.getTerm().equals(snowstormDescription.getTerm()))) {
					// Description in Snowstorm does not match any of the uploaded descriptions. Make the Snowstorm description inactive.
					snowstormDescription.getAcceptabilityMap().remove(languageRefsetId);
					anyChange = true;
					changeSummary.incrementRemoved();// Removed from the language refset
					changeMonitor.removed(conceptId, snowstormDescription.toString());

					// Also suggest that Snowstorm deletes / inactivates this description if not used by any other lang refset
					if (snowstormDescription.getAcceptabilityMap().isEmpty()) {
						toRemove.add(snowstormDescription);// Snowstorm will delete or inactivate component depending on release status
					}
				}
			}
		}
		existingDescriptions.removeAll(toRemove);

		// Add any missing descriptions in the snowstorm concept
		boolean descriptionChange;
		for (Description uploadedDescription : uploadedDescriptions) {
			// Match by language and term only
			Optional<Description> existingDescriptionOptional = existingDescriptions.stream()
					.filter(d -> d.getLang().equals(languageCode) && d.getTerm().equals(uploadedDescription.getTerm())).findFirst();

			descriptionChange = false;

			if (existingDescriptionOptional.isPresent()) {
				Description existingDescription = existingDescriptionOptional.get();

				if (!existingDescription.isActive()) {// Reactivation
					existingDescription.setActive(true);
					anyChange = true;
					descriptionChange = true;
				}

				Description.CaseSignificance uploadedCaseSignificance = uploadedDescription.getCaseSignificance();
				if (uploadedCaseSignificance != null && existingDescription.getCaseSignificance() != uploadedCaseSignificance) {
					existingDescription.setCaseSignificance(uploadedCaseSignificance);
					anyChange = true;
					descriptionChange = true;
				}

				Description.Acceptability newAcceptability = uploadedDescription.getAcceptabilityMap().get(languageRefsetId);
				Description.Acceptability existingAcceptability;
				if (newAcceptability == null) {
					existingAcceptability = existingDescription.getAcceptabilityMap().remove(languageRefsetId);
				} else {
					existingAcceptability = existingDescription.getAcceptabilityMap().put(languageRefsetId, newAcceptability);
				}
				if (newAcceptability != existingAcceptability) {
					logger.debug("Correcting acceptability of {} '{}' from {} to {}",
							existingDescription.getDescriptionId(), existingDescription.getTerm(), existingAcceptability, newAcceptability);
					anyChange = true;
					descriptionChange = true;
				}

				if (anyChange) {
					changeSummary.incrementUpdated();
				}
				if (descriptionChange) {
					changeMonitor.updated(conceptId, existingDescription.toString());
				} else {
					changeMonitor.noChange(conceptId, existingDescription.toString());
				}
			} else {
				// no existing match, create new
				if (uploadedDescription.getCaseSignificance() == null) {
					Description.CaseSignificance caseSignificance = guessCaseSignificance(uploadedDescription.getTerm(), translationTermsUseTitleCase, existingDescriptions);
					uploadedDescription.setCaseSignificance(caseSignificance);
				}
				existingDescriptions.add(uploadedDescription);
				anyChange = true;
				changeSummary.incrementAdded();
				changeMonitor.added(conceptId, uploadedDescription.toString());
			}
		}

		// Remove existing lang refset entries on inactive descriptions
		for (Description snowstormDescription : existingDescriptions) {
			if (snowstormDescription.getLang().equals(languageCode)
					&& !snowstormDescription.isActive()
					&& snowstormDescription.getAcceptabilityMap().containsKey(languageRefsetId)) {

				snowstormDescription.getAcceptabilityMap().remove(languageRefsetId);
				anyChange = true;
				changeSummary.incrementRemoved();// Removed from the language refset
				changeMonitor.removed(conceptId, snowstormDescription.toString());
				logger.info("Removed redundant lang refset on concept {}, description {}.", conceptId, snowstormDescription.getDescriptionId());
			}
		}
		return anyChange;
	}

	private CSVOutputChangeMonitor getCsvOutputChangeMonitor() throws ServiceException {
		CSVOutputChangeMonitor changeMonitor;
		try {
			Path tempFile = Files.createTempFile(UUID.randomUUID().toString(), ".txt");
			changeMonitor = new CSVOutputChangeMonitor(new FileOutputStream(tempFile.toFile()));
			logger.info("Created change report file {}", tempFile.toFile().getAbsolutePath());
		} catch (IOException e) {
			throw new ServiceException("Failed to create temp file for change report.", e);
		}
		return changeMonitor;
	}

	private Map<Long, List<Description>> readTranslationsFromWeblateCSV(InputStream inputStream, String languageCode, String languageRefsetId) throws ServiceException {

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			Map<Long, List<Description>> conceptDescriptions = new Long2ObjectOpenHashMap<>();
			String header = reader.readLine();
			if (header == null) {
				header = "";
			}
			header = header.replace("\"", "");
			if (!header.equals("source,target,context,developer_comments")) {
				throw new ServiceException(format("Unrecognised CSV header '%s'", header));
			}

			logger.info("Confirmed Weblate CSV format");
			String line;
			int lineNumber = 1;
			Set<Long> conceptsCovered = new LongOpenHashSet();
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				String[] columns = line.split("\",\"");
				String translatedTerm;
				String conceptString;
				if (columns.length == 4) {
					if (columns[2].isBlank() && columns[3].equals("\"")) {
						// Strange case - weblate exports synonyms as "concept id", "term"
						translatedTerm = columns[1];
						conceptString = columns[0];
					} else {
						// source	target	context	developer_comments
						// 0		1		2		3
						translatedTerm = columns[1];
						conceptString = columns[2];
					}
				} else {
					logger.warn("Line {} has less than 4 columns, skipping: {}", lineNumber, columns);
					continue;
				}
				translatedTerm = translatedTerm.replace("\"", "");
				conceptString = conceptString.replace("\"", "");
				if (!translatedTerm.isEmpty() && conceptString.matches("\\d+")) {
					Long conceptId = parseLong(conceptString);
					// First term in the spreadsheet is "preferred"
					boolean firstTermForConcept = conceptsCovered.add(conceptId);
					Description.Acceptability acceptability = firstTermForConcept ? Description.Acceptability.PREFERRED : Description.Acceptability.ACCEPTABLE;
					conceptDescriptions.computeIfAbsent(conceptId, id -> new ArrayList<>())
							.add(new Description(SYNONYM, languageCode, translatedTerm, null, languageRefsetId, acceptability));
				}
			}
			return conceptDescriptions;
		} catch (IOException e) {
			throw new ServiceException("Failed to read translation CSV.", e);
		}
	}

	protected Description.CaseSignificance guessCaseSignificance(String term, boolean titleCaseUsed, List<Description> otherDescriptions) {
		if (term.isEmpty()) {
			return CASE_INSENSITIVE;
		}
		if (otherDescriptions != null) {
			// If first word matches an existing description use case sensitivity.
			// Doesn't make complete sense to me but there is a drools rule stating this.
			String firstWord = getFirstWord(term);
			for (Description otherDescription : otherDescriptions) {
				if (getFirstWord(otherDescription.getTerm()).equals(firstWord)) {
					return otherDescription.getCaseSignificance();
				}
			}
		}
		if (titleCaseUsed) {
			// Ignore first character by removing it
			term = term.substring(1);
		}
		return term.equals(term.toLowerCase()) ? CASE_INSENSITIVE : ENTIRE_TERM_CASE_SENSITIVE;
	}

	private static String getFirstWord(String term) {
		return term.split(" ", 2)[0];
	}
}
