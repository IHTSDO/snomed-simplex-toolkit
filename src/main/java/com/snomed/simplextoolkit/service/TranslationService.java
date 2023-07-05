package com.snomed.simplextoolkit.service;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Lists;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.domain.ProgressMonitor;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.LanguageCode;
import com.snomed.simplextoolkit.util.TimerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static com.snomed.simplextoolkit.client.domain.Description.CaseSignificance.CASE_INSENSITIVE;
import static com.snomed.simplextoolkit.client.domain.Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
import static com.snomed.simplextoolkit.client.domain.Description.Type.SYNONYM;
import static java.lang.Long.parseLong;
import static java.lang.String.format;

@Service
public class TranslationService {

	private final List<LanguageCode> languageCodes = new ArrayList<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/language_codes_iso-639-1.txt")))) {
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

		return doUploadTranslation(() -> readTranslationsFromWeblateCSV(inputStream, languageCode, languageRefsetId),
				languageRefsetId, translationTermsUseTitleCase, codeSystem, snowstormClient, progressMonitor);
	}

	public ChangeSummary uploadTranslationAsRefsetToolArchive(String languageRefsetId, CodeSystem codeSystem, InputStream inputStream,
			SnowstormClient snowstormClient, ProgressMonitor progressMonitor) throws ServiceException {

		return doUploadTranslation(() -> new RefsetToolTranslationZipReader(inputStream, languageRefsetId).readUpload(),
				languageRefsetId, true, codeSystem, snowstormClient, progressMonitor);
	}

	public ChangeSummary doUploadTranslation(TranslationUploadProvider uploadProvider, String languageRefsetId,
			boolean translationTermsUseTitleCase, CodeSystem codeSystem, SnowstormClient snowstormClient, ProgressMonitor progressMonitor) throws ServiceException {

		logger.info("Reading translation file..");
		Map<Long, List<Description>> conceptDescriptions = uploadProvider.readUpload();

		logger.info("Read translation terms for {} concepts", conceptDescriptions.size());
		progressMonitor.setRecordsTotal(conceptDescriptions.size());

		int added = 0;
		int updated = 0;
		int removed = 0;

		if (conceptDescriptions.isEmpty()) {
			// No change
			int activeRefsetMembers = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
			return new ChangeSummary(added, updated, removed, activeRefsetMembers);
		}

		// Grab language code from first uploaded description
		String languageCode = conceptDescriptions.values().iterator().next().get(0).getLang();
		int processed = 0;
		int batchSize = 500;
		for (List<Long> conceptIdBatch : Lists.partition(new ArrayList<>(conceptDescriptions.keySet()), batchSize)) {
			if (processed > 0 && processed % 1_000 == 0) {
				logger.info("Processed {} / {}", processed, conceptDescriptions.size());
			}
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
			List<Concept> conceptsToUpdate = new ArrayList<>();
			for (Concept concept : concepts) {
				boolean anyChange = false;
				List<Description> uploadedDescriptions = conceptDescriptions.get(parseLong(concept.getConceptId()));
				List<Description> snowstormDescriptions = concept.getDescriptions();
				snowstormDescriptions.sort(Comparator.comparing(Description::isActive).reversed());

				// Remove any descriptions in snowstorm with a matching concept, language and lang refset if they are not in the latest CSV
				List<Description> toRemove = new ArrayList<>();
				for (Description snowstormDescription : snowstormDescriptions) {
					if (snowstormDescription.getLang().equals(languageCode)
							&& snowstormDescription.isActive()
							&& snowstormDescription.getAcceptabilityMap().containsKey(languageRefsetId)) {

						if (uploadedDescriptions.stream().noneMatch(uploadedDescription -> uploadedDescription.getTerm().equals(snowstormDescription.getTerm()))) {
							// Description in Snowstorm does not match any of the uploaded descriptions. Make the Snowstorm description inactive.
							snowstormDescription.getAcceptabilityMap().remove(languageRefsetId);
							anyChange = true;
							removed++;// Removed from the language refset

							// Also suggest that Snowstorm deletes / inactivates this description if not used by any other lang refset
							if (snowstormDescription.getAcceptabilityMap().isEmpty()) {
								toRemove.add(snowstormDescription);// Snowstorm will delete or inactivate component depending on release status
							}
						}
					}
				}
				snowstormDescriptions.removeAll(toRemove);

				// Add any missing descriptions in the snowstorm concept
				for (Description uploadedDescription : uploadedDescriptions) {
					// Match by language and term only
					Optional<Description> existingDescriptionOptional = snowstormDescriptions.stream()
							.filter(d -> d.getLang().equals(languageCode) && d.getTerm().equals(uploadedDescription.getTerm())).findFirst();

					if (existingDescriptionOptional.isPresent()) {
						Description existingDescription = existingDescriptionOptional.get();

						if (!existingDescription.isActive()) {// Reactivation
							existingDescription.setActive(true);
							anyChange = true;
						}

						Description.CaseSignificance uploadedCaseSignificance = uploadedDescription.getCaseSignificance();
						if (uploadedCaseSignificance != null && existingDescription.getCaseSignificance() != uploadedCaseSignificance) {
							existingDescription.setCaseSignificance(uploadedCaseSignificance);
							anyChange = true;
						}

						Description.Acceptability newAcceptability = uploadedDescription.getAcceptabilityMap().get(languageRefsetId);
						Description.Acceptability existingAcceptability = existingDescription.getAcceptabilityMap().put(languageRefsetId, newAcceptability);
						if (newAcceptability != existingAcceptability) {
							logger.debug("Correcting acceptability of {} '{}' from {} to {}",
									existingDescription.getDescriptionId(), existingDescription.getTerm(), existingAcceptability, newAcceptability);
							anyChange = true;
						}

						if (anyChange) {
							updated++;
						}
					} else {
						// no existing match, create new
						if (uploadedDescription.getCaseSignificance() == null) {
							Description.CaseSignificance caseSignificance = guessCaseSignificance(uploadedDescription.getTerm(), translationTermsUseTitleCase);
							uploadedDescription.setCaseSignificance(caseSignificance);
						}
						snowstormDescriptions.add(uploadedDescription);
						anyChange = true;
						added++;
					}
				}

				if (anyChange) {
					conceptsToUpdate.add(concept);
				}
			}
			if (!conceptsToUpdate.isEmpty()) {
				logger.info("Updating {} concepts on {}", conceptsToUpdate.size(), codeSystem.getBranchPath());
				snowstormClient.updateBrowserFormatConcepts(conceptsToUpdate, codeSystem);
			}
			processed += conceptIdBatch.size();
			progressMonitor.setRecordsProcessed(processed);
		}

		int newActiveCount = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
		ChangeSummary changeSummary = new ChangeSummary(added, updated, removed, newActiveCount);
		logger.info("translation upload complete on {}: {}", codeSystem.getBranchPath(), changeSummary);
		return changeSummary;
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

	protected Description.CaseSignificance guessCaseSignificance(String term, boolean titleCaseUsed) {
		if (term.isEmpty()) {
			return CASE_INSENSITIVE;
		}
		if (titleCaseUsed) {
			// Ignore first character by removing it
			term = term.substring(1);
		}
		return term.equals(term.toLowerCase()) ? CASE_INSENSITIVE : ENTIRE_TERM_CASE_SENSITIVE;
	}
}
