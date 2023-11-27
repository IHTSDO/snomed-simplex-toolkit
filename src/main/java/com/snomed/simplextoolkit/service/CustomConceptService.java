package com.snomed.simplextoolkit.service;

import com.google.common.collect.Lists;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.domain.ConceptIntent;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.job.ChangeSummary;
import com.snomed.simplextoolkit.service.job.ContentJob;
import com.snomed.simplextoolkit.service.job.DummyChangeMonitor;
import com.snomed.simplextoolkit.service.spreadsheet.HeaderConfiguration;
import com.snomed.simplextoolkit.service.spreadsheet.SheetHeader;
import com.snomed.simplextoolkit.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.snomed.simplextoolkit.client.domain.Concepts.IS_A;
import static com.snomed.simplextoolkit.service.SpreadsheetService.readSnomedConcept;
import static java.lang.String.format;
import static java.util.function.Predicate.not;

@Service
public class CustomConceptService {

	@Autowired
	private TranslationService translationService;

	@Autowired
	private SpreadsheetService spreadsheetService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void downloadSpreadsheet(CodeSystem codeSystem, SnowstormClient snowstormClient, ServletOutputStream outputStream) throws ServiceException {
		logger.info("Creating custom concept spreadsheet for {}", codeSystem.getShortName());
		List<ConceptMini> langRefsets = translationService.listTranslations(codeSystem, snowstormClient);
		String defaultModule = codeSystem.getDefaultModule();
		List<Long> conceptIds = snowstormClient.findConceptsByModule(codeSystem, defaultModule).stream().map(ConceptMini::getConceptId).map(Long::parseLong).toList();
		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
		try (Workbook workbook = spreadsheetService.createConceptSpreadsheet(getInputSheetHeaders(translationService.listTranslations(codeSystem, snowstormClient)), concepts, langRefsets)) {
			workbook.write(outputStream);
		} catch (IOException e) {
			throw new ServiceException("Failed to write concept spreadsheet to API response stream.", e);
		}
	}

	public ChangeSummary uploadSpreadsheet(CodeSystem codeSystem, InputStream inputStream, SnowstormClient snowstormClient, ContentJob asyncJob) throws ServiceException {
		List<ConceptMini> langRefsets = translationService.listTranslations(codeSystem, snowstormClient);
		List<String> extensionLangRefsets = langRefsets.stream().map(ConceptMini::getConceptId).toList();
		Set<String> allLangRefsetsInScope = new HashSet<>(extensionLangRefsets);
		allLangRefsetsInScope.add(Concepts.US_LANG_REFSET);
		List<ConceptIntent> sheetConcepts = spreadsheetService.readComponentSpreadsheet(inputStream, getInputSheetHeaders(langRefsets), getInputSheetComponentExtractor(extensionLangRefsets));
		return createUpdateConcepts(codeSystem, sheetConcepts, allLangRefsetsInScope, asyncJob, snowstormClient);
	}

	protected ChangeSummary createUpdateConcepts(CodeSystem codeSystem, List<ConceptIntent> conceptIntents, Collection<String> langRefsetIds,
			ContentJob asyncJob, SnowstormClient snowstormClient) throws ServiceException {

		ChangeSummary changeSummary = new ChangeSummary();
		changeSummary.setNewTotal(conceptIntents.size());
		asyncJob.setRecordsTotal(conceptIntents.size());
		String jobId = asyncJob.getId();
		String defaultModule = codeSystem.getDefaultModuleOrThrow();

		for (List<ConceptIntent> intents : Lists.partition(conceptIntents, 100)) {
			logger.info("Processing concept updates, batch of {}, Branch:{}, Job:{}", intents.size(), codeSystem.getWorkingBranchPath(), jobId);
			Set<String> parentCodes = intents.stream().map(ConceptIntent::getParentCode).filter(not(String::isEmpty)).collect(Collectors.toSet());
			Set<String> existingConceptCodes = intents.stream().map(ConceptIntent::getConceptCode).filter(Objects::nonNull).collect(Collectors.toSet());

			Map<String, Concept> parentConceptMap = snowstormClient.loadBrowserFormatConcepts(parentCodes.stream().map(Long::parseLong).toList(), codeSystem)
					.stream().collect(Collectors.toMap(Concept::getConceptId, Function.identity()));

			Map<String, Concept> existingConceptMap = snowstormClient.loadBrowserFormatConcepts(existingConceptCodes.stream().map(Long::parseLong).toList(), codeSystem)
					.stream().collect(Collectors.toMap(Concept::getConceptId, Function.identity()));

			logger.info("Loaded {} parents and {} existing concepts, Job:{}", parentConceptMap.size(), existingConceptMap.size(), jobId);

			List<Concept> conceptsToSave = new ArrayList<>();
			for (ConceptIntent intent : intents) {
				Concept concept;
				String conceptCode = intent.getConceptCode();
				Concept parentConcept = null;
				if (!intent.isInactive()) {
					parentConcept = getParentConceptOrThrow(intent, parentConceptMap, intent.getRowNumber());
				}

				boolean changed = false;
				if (conceptCode != null) {
					// Existing concept
					concept = existingConceptMap.get(conceptCode);
					if (concept == null) {
						throw new ServiceException(format("Concept with code '%s' on row %s could not be found.", conceptCode, intent.getRowNumber()));
					}
					if (!concept.getModuleId().equals(defaultModule)) {
						throw new ServiceException(format("Concept with code '%s' on row %s is from a different module and can not be modified using this function.",
								conceptCode, intent.getRowNumber()));
					}

					if (intent.isInactive()) {
						if (concept.isActive()) {
							concept.setActive(false);// Snowstorm will delete this concept automatically if it has never been released.
							concept.getClassAxioms().forEach(axiom -> axiom.setActive(false));
							changed = true;
							changeSummary.incrementRemoved();
						}
					} else {
						boolean relationshipAlreadyCorrect = false;
						if (concept.getClassAxioms().size() == 1) {
							Axiom axiom = concept.getClassAxioms().get(0);
							if (axiom.getRelationships().size() == 1) {
								Relationship relationship = axiom.getRelationships().get(0);
								if (IS_A.equals(relationship.getTypeId()) && parentConcept.getConceptId().equals(relationship.getDestinationId())) {
									relationshipAlreadyCorrect = true;
								}
							}
						}
						if (!relationshipAlreadyCorrect) {
							concept.setClassAxioms(Collections.singletonList(
									new Axiom("PRIMITIVE", Collections.singletonList(Relationship.stated(IS_A, parentConcept.getConceptId(), 0)))));
							copyInferredRelationshipsFromParent(concept, parentConcept);
							changed = true;
							changeSummary.incrementUpdated();
						}
					}
				} else {
					// New concept
					concept = new Concept(defaultModule)
							.addAxiom(new Axiom("PRIMITIVE", Collections.singletonList(Relationship.stated(IS_A, parentConcept.getConceptId(), 0))));
					copyInferredRelationshipsFromParent(concept, parentConcept);
					changeSummary.incrementAdded();
					changed = true;
				}

				if (!intent.isInactive()) {
					// Build set of needed descriptions
					Map<String, Description> termLangDescriptionMap = new HashMap<>();// Map to reuse description if lang + term is the same
					for (String langRefsetId : langRefsetIds) {
						String language = "en";
						List<String> terms = intent.getLangRefsetTerms().getOrDefault(langRefsetId, Collections.emptyList());
						if (!terms.isEmpty()) {
							// Create PT from first term in the list
							String pt = terms.get(0);
							termLangDescriptionMap.computeIfAbsent(language + "_" + pt,
											i -> new Description(Description.Type.SYNONYM, language, pt, null))
									.addAcceptability(langRefsetId, Description.Acceptability.PREFERRED);

							// If US English also create an FSN using PT and stated parent semantic tag
							if (langRefsetId.equals(Concepts.US_LANG_REFSET)) {
								String enSemanticTag = parentConcept.getEnSemanticTag();
								String fsn = String.format("%s %s", pt, enSemanticTag);
								termLangDescriptionMap.put(language + "_" + fsn,
										new Description(Description.Type.FSN, language, fsn, null)
												.addAcceptability(langRefsetId, Description.Acceptability.PREFERRED));
							}

							// Create acceptable synonyms from all other terms in this lang refset
							for (int i = 1; i < terms.size(); i++) {
								String term = terms.get(i);
								termLangDescriptionMap.computeIfAbsent(language + "_" + term,
												t -> new Description(Description.Type.SYNONYM, language, term, null))
										.addAcceptability(langRefsetId, Description.Acceptability.ACCEPTABLE);
							}
						}
					}

					// Merge into existing descriptions
					boolean existingConceptNotYetChanged = concept.getConceptId() != null && !changed;
					List<Description> descriptions = new ArrayList<>(termLangDescriptionMap.values());
					for (String langRefsetId : langRefsetIds) {
						boolean updateConceptDescriptions = translationService.updateConceptDescriptions(concept.getConceptId(), concept.getDescriptions(), descriptions,
								"en", langRefsetId, true, new DummyChangeMonitor(), new ChangeSummary());
						if (updateConceptDescriptions) {
							if (existingConceptNotYetChanged) {
								existingConceptNotYetChanged = false;
								changeSummary.incrementUpdated();
							}
							changed = true;
						}
					}
				}

				if (changed) {
					conceptsToSave.add(concept);
				}
				asyncJob.incrementRecordsProcessed();
			}
			if (!conceptsToSave.isEmpty()) {
				logger.info("Create/Update {} concepts on {}, Job:{}", conceptsToSave.size(), codeSystem.getWorkingBranchPath(), jobId);
				snowstormClient.createUpdateBrowserFormatConcepts(conceptsToSave, codeSystem);
			} else {
				logger.info("No concept update needed on {}, Job:{}", codeSystem.getWorkingBranchPath(), jobId);
			}
		}
		return changeSummary;
	}

	// Copying the inferred relationships from the parent to a primitive concept gives a good preview of the inferred form
	private void copyInferredRelationshipsFromParent(Concept concept, Concept parentConcept) {
		List<Relationship> relationships = new ArrayList<>();
		relationships.add(Relationship.inferred(IS_A, parentConcept.getConceptId(), 0));
		parentConcept.getRelationships().stream()
				.filter(relationship -> relationship.isActive() && !relationship.getTypeId().equals(IS_A) && relationship.getCharacteristicType().equals("INFERRED_RELATIONSHIP"))
						.forEach(rel -> relationships.add(Relationship.inferred(rel.getTypeId(), rel.getDestinationId(), rel.getGroupId())));
		concept.setRelationships(relationships);
	}

	private static Concept getParentConceptOrThrow(ConceptIntent intent, Map<String, Concept> parentConceptMap, int row) throws ServiceException {
		Concept parentConcept = parentConceptMap.get(intent.getParentCode());
		if (parentConcept == null) {
			throw new ServiceException(format("Parent concept with code '%s' on row %s could not be found.", intent.getParentCode(), row));
		}
		return parentConcept;
	}

	private List<SheetHeader> getInputSheetHeaders(List<ConceptMini> langRefsets) {
		List<SheetHeader> headers = new ArrayList<>();
		headers.add(new SheetHeader("Parent Concept Identifier").setSubtitle("Only one can be given per concept."));
		headers.add(new SheetHeader("Parent Concept Term").setSubtitle("This is just for reference."));
		headers.add(new SheetHeader("Concept Identifier").setSubtitle("Do not change this column. \r\n" +
				"Concept identifiers will be generated."));
		headers.add(new SheetHeader("Active").setSubtitle("Leave this column blank to start with. \r\n" +
				"To make a concept inactive enter a value of false."));
		String termSubtitle = "The first term will be the preferred term. \r\n" +
				"Each additional synonym should use a new row. There is no need to repeat the values in columns A to D.";
		headers.add(new SheetHeader("Terms in English, US dialect").setSubtitle(termSubtitle));
		for (ConceptMini langRefset : langRefsets) {
			headers.add(new SheetHeader(format("Terms in %s (%s)", langRefset.getPt().getTerm(), langRefset.getConceptId())).setSubtitle(termSubtitle).optional());
		}
		return headers;
	}

	private SheetRowToComponentIntentExtractor<ConceptIntent> getInputSheetComponentExtractor(final List<String> langRefsetIds) {

		return new SheetRowToComponentIntentExtractor<>() {

			private static final String PARENT_CODE = "Parent Concept Identifier";
			private static final String CONCEPT_CODE = "Concept Identifier";
			private static final String ACTIVE = "Active";
			private static final String TERMS_EN_US = "Terms in English, US dialect";

			private ConceptIntent mainComponent;
			private boolean headerValidationComplete;
			private final Map<Integer, String> termColumnIndexToLangRefsetMap = new HashMap<>();
			private final Pattern langRefsetIdPattern = Pattern.compile("Terms in .*\\(([0-9]+)\\).*");

			@Override
			public ConceptIntent extract(Row row, Integer rowNumber, HeaderConfiguration headerConfiguration) throws ServiceException {
				if (!headerValidationComplete) {
					Integer enUsIndex = headerConfiguration.getColumnStarting(TERMS_EN_US);
					if (enUsIndex == null) {
						throw new ServiceException(format("Mandatory column not found. Please ensure there is a column with title starting '%s'", TERMS_EN_US));
					}
					termColumnIndexToLangRefsetMap.put(enUsIndex, Concepts.US_LANG_REFSET);

					List<Integer> languageHeaders = headerConfiguration.getColumnsMatching("Terms in .*\\)");
					for (Integer languageHeaderColumn : languageHeaders) {
						SheetHeader header = headerConfiguration.getHeader(languageHeaderColumn);
						String name = header.getName();
						Matcher matcher = langRefsetIdPattern.matcher(name);
						if (matcher.matches()) {
							String langRefsetId = matcher.group(1);
							if (langRefsetIds.contains(langRefsetId)) {
								termColumnIndexToLangRefsetMap.put(languageHeaderColumn, langRefsetId);
							} else {
								throw new ServiceException(format("The language reference set code given in the header of column %s is not one of the expected values.",
										languageHeaderColumn + 1));
							}
						} else {
							throw new ServiceException(format("The header of column %s does not match the expected pattern for language reference sets.",
									languageHeaderColumn + 1));
						}
					}
					headerValidationComplete = true;
				}

				ConceptIntent conceptIntent;

				String parentCode = readSnomedConcept(row, headerConfiguration.getColumn(PARENT_CODE), rowNumber);
				if (parentCode != null) {
					conceptIntent = new ConceptIntent(parentCode, rowNumber);
					boolean inactive = row.getCell(headerConfiguration.getColumn(ACTIVE), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue().equalsIgnoreCase("false");
					conceptIntent.setInactive(inactive);
					mainComponent = conceptIntent;
					String conceptCode = readSnomedConcept(row, headerConfiguration.getColumn(CONCEPT_CODE), rowNumber);
					conceptIntent.setConceptCode(conceptCode);
				} else {
					conceptIntent = mainComponent;
				}
				if (conceptIntent == null) {
					return null;
				}

				for (Map.Entry<Integer, String> entry : termColumnIndexToLangRefsetMap.entrySet()) {
					Cell cell = row.getCell(entry.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
					if (cell != null) {
						conceptIntent.addTerm(cell.getStringCellValue(), entry.getValue());
					}
				}

				// If there was no parent code then we updated the previous concept, don't return it again
				return parentCode != null ? conceptIntent : null;
			}
		};
	}
}
