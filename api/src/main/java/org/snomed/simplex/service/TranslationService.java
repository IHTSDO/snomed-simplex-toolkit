package org.snomed.simplex.service;

import ch.qos.logback.classic.Level;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LanguageCode;
import org.snomed.simplex.service.job.ChangeMonitor;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.util.FileUtils;
import org.snomed.simplex.util.TimerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static org.snomed.simplex.client.domain.Description.CaseSignificance.*;
import static org.snomed.simplex.client.domain.Description.Type.FSN;
import static org.snomed.simplex.client.domain.Description.Type.SYNONYM;

@Service
public class TranslationService {

	public static final String NON_BREAKING_SPACE_CHARACTER = " ";
	public static final String EN_DASH = "–";
	public static final String EM_DASH = "—";

	public static final Pattern TITLE_CASE_UPPER_CASE_SECOND_LETTER_PATTERN = Pattern.compile(".\\p{Upper}.*", Pattern.UNICODE_CHARACTER_CLASS);
	public static final Pattern TITLE_CASE_LOWER_CASE_SECOND_LETTER_BUT_UPPER_LATER = Pattern.compile(".\\p{Lower}.*\\p{Upper}.*", Pattern.UNICODE_CHARACTER_CLASS);
	public static final Pattern UPPER_CASE_FIRST_LETTER_PATTERN = Pattern.compile("\\p{Upper}.*", Pattern.UNICODE_CHARACTER_CLASS);
	public static final Pattern LOWER_CASE_FIRST_LETTER_BUT_UPPER_LATER = Pattern.compile("\\p{Lower}.*\\p{Upper}.*", Pattern.UNICODE_CHARACTER_CLASS);

	private final List<LanguageCode> languageCodes = new ArrayList<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final SimpleRefsetService refsetService;
	private final SnowstormClientFactory snowstormClientFactory;

	public TranslationService(SimpleRefsetService refsetService, SnowstormClientFactory snowstormClientFactory) {
		this.refsetService = refsetService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

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

	public Concept createTranslation(String preferredTerm, String languageCode, CodeSystem codeSystem) throws ServiceException {
		validateCreateRequest(preferredTerm, languageCode);

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		Concept concept = snowstormClient.createSimpleMetadataConcept(Concepts.LANG_REFSET, preferredTerm,
				Concepts.FOUNDATION_METADATA_CONCEPT_TAG, codeSystem);

		snowstormClient.addTranslationLanguage(concept.getConceptId(), languageCode, codeSystem, snowstormClient);
		return concept;
	}

	private void validateCreateRequest(String preferredTerm, String languageCode) throws ServiceExceptionWithStatusCode {
		if (Strings.isNullOrEmpty(preferredTerm)) {
			throw new ServiceExceptionWithStatusCode("Translation term is required.", HttpStatus.BAD_REQUEST, JobStatus.USER_CONTENT_ERROR);
		}
		if (Strings.isNullOrEmpty(languageCode)) {
			throw new ServiceExceptionWithStatusCode("Translation term is required.", HttpStatus.BAD_REQUEST, JobStatus.USER_CONTENT_ERROR);
		}
		String languageCodeLower = languageCode.toLowerCase();
		if (languageCodes.stream().noneMatch(code -> code.getCode().equals(languageCodeLower))) {
			throw new ServiceExceptionWithStatusCode("Unrecognised language code.", HttpStatus.BAD_REQUEST, JobStatus.USER_CONTENT_ERROR);
		}
	}

	public List<ConceptMini> listTranslations(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		TimerUtil timer = new TimerUtil("Load translations", Level.INFO, 2);

		List<ConceptMini> translationRefsets = snowstormClient.getRefsets("<" + Concepts.LANG_REFSET, codeSystem);
		timer.checkpoint("ECL for lang refsets");

		for (ConceptMini translationRefset : translationRefsets) {
			String langRefsetId = translationRefset.getConceptId();
			String languageCode = codeSystem.getTranslationLanguages().get(langRefsetId);
			if (languageCode == null) {
				// This should rarely happen
				// Search for existing lang refset entries
				Page<RefsetMember> firstMember = snowstormClient.getRefsetMembers(langRefsetId, codeSystem, true, 1, null);
				timer.checkpoint(format("Load one lang refset member for %s.", translationRefset.getIdAndFsnTerm()));

				if (!firstMember.getItems().isEmpty()) {
					RefsetMember member = firstMember.getItems().iterator().next();
					ReferencedComponent referencedComponent = member.getReferencedComponent();
					languageCode = referencedComponent.getLang();
				}
				if (languageCode == null) {
					languageCode = "en";
					logger.warn("Setting language default language code {} for {}, {}",
							languageCode, codeSystem.getShortName(), langRefsetId);
				} else {
					logger.info("Using existing lang refset entries to set language code {} for {}, {}",
							languageCode, codeSystem.getShortName(), langRefsetId);
				}
				snowstormClient.addTranslationLanguage(langRefsetId, languageCode, codeSystem, snowstormClient);
			}

			translationRefset.addExtraField("lang", languageCode);
		}
		return translationRefsets;
	}

	public ChangeSummary uploadTranslationAsWeblateCSV(boolean translationTermsUseTitleCase, ContentJob asyncJob) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return uploadTranslationAsWeblateCSV(asyncJob.getRefsetId(), asyncJob.getCodeSystemObject(), asyncJob.getInputStream(),
				translationTermsUseTitleCase, snowstormClient, asyncJob);
	}

	public ChangeSummary uploadTranslationAsWeblateCSV(String languageRefsetId, CodeSystem codeSystem, InputStream inputStream,
			boolean translationTermsUseTitleCase, SnowstormClient snowstormClient, ProgressMonitor progressMonitor) throws ServiceException {

		String languageCode = getLanguageCodeOrThrow(languageRefsetId, codeSystem);
		try (CSVOutputChangeMonitor changeMonitor = getCsvOutputChangeMonitor()) {
			return doUploadTranslation(() -> readTranslationsFromWeblateCSV(inputStream, languageCode, languageRefsetId),
					languageRefsetId, translationTermsUseTitleCase, codeSystem, snowstormClient, progressMonitor, changeMonitor);
		}
	}

	private static @NotNull String getLanguageCodeOrThrow(String languageRefsetId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		String languageCode = codeSystem.getTranslationLanguages().get(languageRefsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not set for translation.", HttpStatus.CONFLICT, JobStatus.SYSTEM_ERROR);
		}
		return languageCode;
	}

	public ChangeSummary uploadTranslationAsRefsetToolArchive(ContentJob contentJob, boolean ignoreCaseInImport) throws ServiceException {
		return uploadTranslationAsRefsetToolArchive(contentJob.getRefsetId(), contentJob.getCodeSystemObject(), contentJob.getInputStream(),
				ignoreCaseInImport, contentJob);
	}

	public ChangeSummary uploadTranslationAsRefsetToolArchive(String languageRefsetId, CodeSystem codeSystem, InputStream inputStream,
			boolean ignoreCaseInImport, ProgressMonitor progressMonitor) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		try (CSVOutputChangeMonitor changeMonitor = getCsvOutputChangeMonitor()) {
			return doUploadTranslation(() -> new RefsetToolTranslationZipReader(inputStream, languageRefsetId, ignoreCaseInImport).readUpload(),
					languageRefsetId, true, codeSystem, snowstormClient, progressMonitor, changeMonitor);
		}
	}

	private ChangeSummary doUploadTranslation(TranslationUploadProvider uploadProvider, String languageRefsetId,
			boolean translationTermsUseTitleCase, CodeSystem codeSystem, SnowstormClient snowstormClient,
			ProgressMonitor progressMonitor, ChangeMonitor changeMonitor) throws ServiceException {

		logger.info("Reading translation file..");
		Map<Long, List<Description>> conceptDescriptions = uploadProvider.readUpload();

		String languageCode = getLanguageCodeOrThrow(languageRefsetId, codeSystem);
		// Validate language codes
		validateLanguageCodes(conceptDescriptions, languageCode);

		// Replace bad characters
		for (List<Description> conceptDescriptionList : conceptDescriptions.values()) {
			for (Description description : conceptDescriptionList) {
				description.setTerm(replaceBadCharacters(description));
			}
		}

		logger.info("Read translation terms for {} concepts", conceptDescriptions.size());
		progressMonitor.setRecordsTotal(conceptDescriptions.size());

		if (conceptDescriptions.isEmpty()) {
			// No change
			int activeRefsetMembers = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
			return new ChangeSummary(0, 0, 0, activeRefsetMembers);
		}

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
				List<Description> uploadedDescriptions = new ArrayList<>(conceptDescriptions.get(parseLong(concept.getConceptId())));
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

	private @NotNull String replaceBadCharacters(Description description) {
		String fixedTerm = description.getTerm()
				.replace(NON_BREAKING_SPACE_CHARACTER, " ")
				.replace(EN_DASH, "-")
				.replace(EM_DASH, "-");
		fixedTerm = fixedTerm.replaceAll(" +", " ");// Replace multiple spaces with single
		if (!fixedTerm.equals(description.getTerm())) {
			logger.info("Fixed term '{}' > '{}'", description.getTerm(), fixedTerm);
		}
		return fixedTerm;
	}

	private static void validateLanguageCodes(Map<Long, List<Description>> conceptDescriptions, String languageCode) throws ServiceExceptionWithStatusCode {
		long descriptionsWithIncorrectLanguageCode = conceptDescriptions.values().stream().flatMap(Collection::stream)
				.filter(description -> !languageCode.equals(description.getLang())).count();
		if (descriptionsWithIncorrectLanguageCode > 0) {
			throw new ServiceExceptionWithStatusCode(("%s of the uploaded terms have an incorrect language. " +
					"Set language code to '%s' for all terms and try again.").formatted(descriptionsWithIncorrectLanguageCode, languageCode),
					HttpStatus.BAD_REQUEST, JobStatus.USER_CONTENT_ERROR);
		}
	}

	public boolean updateConceptDescriptions(String conceptId, List<Description> existingDescriptions, List<Description> uploadedDescriptions,
			String languageCode, String languageRefsetId, boolean translationTermsUseTitleCase,
			ChangeMonitor changeMonitor, ChangeSummary changeSummary) throws ServiceException {

		boolean anyChange = false;
		existingDescriptions.sort(Comparator.comparing(Description::isActive).reversed());

		// Underscore term used to delete redundant terms
		uploadedDescriptions = uploadedDescriptions.stream().filter(description -> !description.getTerm().equals("_"))
				.collect(Collectors.toList());

		// If an English lang refset has selected acceptable terms but no PT, use the US PT.
		if (!uploadedDescriptions.isEmpty() && "en".equals(languageCode) && getPt(uploadedDescriptions, languageRefsetId) == null) {
			Description usPT = getPt(existingDescriptions, Concepts.US_LANG_REFSET);
			if (usPT != null) {
				uploadedDescriptions.add(usPT);
			}
		}

		// Remove any active descriptions in snowstorm with a matching concept, language and lang refset if the term is not in the latest CSV
		List<Description> toRemove = new ArrayList<>();
		for (Description snowstormDescription : existingDescriptions) {
			if (snowstormDescription.getLang().equals(languageCode)
					&& snowstormDescription.isActive()
					&& snowstormDescription.getAcceptabilityMap().containsKey(languageRefsetId)) {

				if (uploadedDescriptions.stream().noneMatch(uploadedDescription -> uploadedDescription.getTerm().equals(snowstormDescription.getTerm()))) {
					// Description in Snowstorm does not match any of the uploaded descriptions. Remove the acceptability for this lang-refset
					snowstormDescription.getAcceptabilityMap().remove(languageRefsetId);
					anyChange = true;
					changeSummary.incrementRemoved();// Removed from the language refset
					changeMonitor.removed(conceptId, snowstormDescription.toString());

					// Also suggest that Snowstorm deletes / inactivates this description if not used by any other lang refset
					if (snowstormDescription.getAcceptabilityMap().isEmpty()) {
						if (snowstormDescription.isReleased()) {
							// Send the released inactive description rather than removing to ensure the acceptability is cleared in Snowstorm
							snowstormDescription.setActive(false);
						} else {
							toRemove.add(snowstormDescription);
						}
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

			if (uploadedDescription.getCaseSignificance() == null) {
				Description.CaseSignificance caseSignificance = guessCaseSignificance(uploadedDescription.getTerm(), translationTermsUseTitleCase, existingDescriptions);
				uploadedDescription.setCaseSignificance(caseSignificance);
			}

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
				existingDescriptions.add(uploadedDescription);
				anyChange = true;
				changeSummary.incrementAdded();
				changeMonitor.added(conceptId, uploadedDescription.toString());
			}
		}

		// Remove existing lang refset entries on all inactive descriptions
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

	private Description getPt(List<Description> descriptions, String languageRefsetId) {
		return descriptions.stream()
				.filter(Component::isActive)
				.filter(description -> description.getType() == SYNONYM)
				.filter(description -> description.getAcceptabilityMap().get(languageRefsetId) == Description.Acceptability.PREFERRED)
				.findFirst().orElse(null);
	}

	public void deleteTranslationAndMembers(String refsetId, CodeSystem codeSystem) throws ServiceException {
		refsetService.deleteRefsetMembersAndConcept(refsetId, codeSystem);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.removeTranslationLanguage(refsetId, codeSystem);
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
			header = FileUtils.removeUTF8BOM(header);
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

		Description.CaseSignificance caseSignificanceFromOtherDescriptions = getCaseSignificanceFromOtherDescriptions(term, otherDescriptions);
		if (caseSignificanceFromOtherDescriptions != null) {
			return caseSignificanceFromOtherDescriptions;
		}

		if (titleCaseUsed) {
			if (TITLE_CASE_UPPER_CASE_SECOND_LETTER_PATTERN.matcher(term).matches()) {
				return ENTIRE_TERM_CASE_SENSITIVE;
			} else if (TITLE_CASE_LOWER_CASE_SECOND_LETTER_BUT_UPPER_LATER.matcher(term).matches()) {
				return INITIAL_CHARACTER_CASE_INSENSITIVE;
			}
		} else {
			if (UPPER_CASE_FIRST_LETTER_PATTERN.matcher(term).matches()) {
				return ENTIRE_TERM_CASE_SENSITIVE;
			} else if (LOWER_CASE_FIRST_LETTER_BUT_UPPER_LATER.matcher(term).matches()) {
				return INITIAL_CHARACTER_CASE_INSENSITIVE;
			}

		}
		return CASE_INSENSITIVE;
	}

	private static Description.@Nullable CaseSignificance getCaseSignificanceFromOtherDescriptions(String term, List<Description> otherDescriptions) {
		if (otherDescriptions != null) {
			// If first word matches an existing released description in the same concept use case sensitivity.
			// Doesn't make complete sense to me but there is a drools rule stating this.
			String firstWord = getFirstWord(term);
			for (Description otherDescription : otherDescriptions) {
				if (otherDescription.isReleased() && getFirstWord(otherDescription.getTerm()).equals(firstWord)) {
					return otherDescription.getCaseSignificance();
				}
			}
		}
		return null;
	}

	private static String getFirstWord(String term) {
		return term.split(" ", 2)[0];
	}

	public String getWeblateMarkdown(Long conceptId, SnowstormClient snowstormClient) {
		CodeSystem internationalCodeSystem = new CodeSystem("SNOMEDCT", "SNOMEDCT", "MAIN");
		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(List.of(conceptId), internationalCodeSystem);
		if (concepts.isEmpty()) {
			return "Concept not found";
		}
		Concept concept = concepts.get(0);
		List<Pair<String, ConceptMini>> attributes = new ArrayList<>();
		for (Relationship relationship : concept.getRelationships().stream().filter(Relationship::isActive).toList()) {
			if (relationship.getTypeId().equals(Concepts.IS_A)) {
				attributes.add(Pair.of("Parent", relationship.getTarget()));
			} else {
				attributes.add(Pair.of(relationship.getType().getPt().getTerm(), relationship.getTarget()));
			}
		}

		List<Description> activeDescriptions = concept.getDescriptions().stream().filter(Component::isActive).toList();
		Description fsn = activeDescriptions.stream()
				.filter(d -> d.getType() == FSN)
				.filter(d -> d.getAcceptabilityMap().get(Concepts.US_LANG_REFSET) == Description.Acceptability.PREFERRED)
				.findFirst().orElse(new Description());
		Description pt = activeDescriptions.stream()
				.filter(d -> d.getType() == SYNONYM)
				.filter(d -> d.getAcceptabilityMap().get(Concepts.US_LANG_REFSET) == Description.Acceptability.PREFERRED)
				.findFirst().orElse(new Description());
		List<Description> synonyms = activeDescriptions.stream()
				.filter(d -> d.getType() == SYNONYM)
				.filter(d -> d.getAcceptabilityMap().get(Concepts.US_LANG_REFSET) == Description.Acceptability.ACCEPTABLE)
				.toList();

		StringBuilder builder = new StringBuilder(
				"""
						#### Concept Details
						| Descriptions |  | Attributes |  |
						| :------------- | --- | ------------: | :--- |
						""");
		int row = 0;
		appendRow(builder, "_FSN_: ", fsn.getTerm(), attributes, row++);
		appendRow(builder, "_PT_: ", pt.getTerm(), attributes, row++);
		for (Description synonym : synonyms) {
			appendRow(builder, "", synonym.getTerm(), attributes, row++);
		}
		while (row < attributes.size()) {
			appendRow(builder, "", "", attributes, row++);
		}
		return builder.toString();
	}

	private static void appendRow(StringBuilder builder, String type, String term, List<Pair<String, ConceptMini>> attributes, int row) {
		builder.append("| ");
		if (!term.isEmpty()) {
			builder.append(type).append(term);
		}
		builder.append(" |  | ");
		if (attributes.size() > row) {
			Pair<String, ConceptMini> attribute = attributes.get(row);
			builder.append("_").append(attribute.getLeft()).append("_ →");
			builder.append(" |  ");
			ConceptMini target = attribute.getRight();
			builder.append(target.getFsn().getTerm()).append(" [open](http://snomed.info/id/").append(target.getConceptId()).append(")");
		}
		builder.append(" |\n");
	}
}
