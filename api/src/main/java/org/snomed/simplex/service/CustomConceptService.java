package org.snomed.simplex.service;

import com.google.common.collect.Lists;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.ConceptIntent;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.service.job.DummyChangeMonitor;
import org.snomed.simplex.service.spreadsheet.HeaderConfiguration;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static org.snomed.simplex.client.domain.Concepts.IS_A;
import static org.snomed.simplex.client.domain.Concepts.US_LANG_REFSET;

@Service
public class CustomConceptService {

	private final TranslationService translationService;
	private final SpreadsheetService spreadsheetService;
	private final SnowstormClientFactory snowstormClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CustomConceptService(TranslationService translationService, SpreadsheetService spreadsheetService,
			SnowstormClientFactory snowstormClientFactory) {
		this.translationService = translationService;
		this.spreadsheetService = spreadsheetService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

	public Page<ConceptMini> findCustomConcepts(CodeSystem codeSystem, SnowstormClient snowstormClient, int offset, int limit) throws ServiceException {
		String defaultModule = codeSystem.getDefaultModuleOrThrow();
		return snowstormClient.findConceptsByModule(codeSystem, defaultModule, offset, limit);
	}

	public void downloadSpreadsheet(CodeSystem codeSystem, SnowstormClient snowstormClient, OutputStream outputStream) throws ServiceException {
		logger.info("Creating custom concept spreadsheet for {}", codeSystem.getShortName());
		List<ConceptMini> langRefsets = translationService.listTranslations(codeSystem, snowstormClient);
		String defaultModule = codeSystem.getDefaultModule();
		List<Long> conceptIds = snowstormClient.findAllConceptsByModule(codeSystem, defaultModule).stream().map(ConceptMini::getConceptId).map(Long::parseLong).toList();
		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
		try (Workbook workbook = spreadsheetService.createConceptSpreadsheet(getInputSheetHeaders(langRefsets), concepts, langRefsets, codeSystem.getContentHeadTimestamp())) {
			workbook.write(outputStream);
		} catch (IOException e) {
			throw new ServiceException("Failed to write concept spreadsheet to API response stream.", e);
		}
	}

	public ChangeSummary uploadSpreadsheet(ContentJob asyncJob) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return uploadSpreadsheet(asyncJob.getCodeSystemObject(), asyncJob.getInputStream(), snowstormClient, asyncJob);
	}

	public ChangeSummary uploadSpreadsheet(CodeSystem codeSystem, InputStream inputStream, SnowstormClient snowstormClient, ContentJob asyncJob) throws ServiceException {
		List<ConceptMini> langRefsets = translationService.listTranslations(codeSystem, snowstormClient);
		List<String> extensionLangRefsets = langRefsets.stream().map(ConceptMini::getConceptId).toList();
		Set<String> allLangRefsetsInScope = new HashSet<>(extensionLangRefsets);
		allLangRefsetsInScope.add(Concepts.US_LANG_REFSET);
		List<ConceptIntent> sheetConcepts = spreadsheetService.readComponentSpreadsheet(inputStream, getInputSheetHeaders(langRefsets),
				getInputSheetComponentExtractor(extensionLangRefsets), codeSystem.getContentHeadTimestamp());
		return createUpdateConcepts(codeSystem, sheetConcepts, allLangRefsetsInScope, asyncJob, snowstormClient);
	}

	protected ChangeSummary createUpdateConcepts(CodeSystem codeSystem, List<ConceptIntent> conceptIntents, Collection<String> langRefsetIds,
			ContentJob asyncJob, SnowstormClient snowstormClient) throws ServiceException {

		ChangeSummary changeSummary = new ChangeSummary();
		changeSummary.setNewTotal(conceptIntents.size());
		asyncJob.setRecordsTotal(conceptIntents.size());
		String jobId = asyncJob.getId();
		String defaultModule = codeSystem.getDefaultModuleOrThrow();

		// Clean concept intents
		replaceNonBreakingSpaces(conceptIntents);

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
					if (concept.getRelationships() == null) {
						concept.setRelationships(new ArrayList<>());
					}
					if (concept.getClassAxioms() == null) {
						concept.setClassAxioms(new ArrayList<>());
					}
					if (!concept.getModuleId().equals(defaultModule)) {
						if (!concept.isActive()) {
							// Reactivation of concept from dependant module
							concept.setEffectiveTime(null);
							concept.setModuleId(defaultModule);
							concept.setInactivationIndicator(null);
							concept.setAssociationTargets(Collections.emptyMap());
							changed = true;
							changeSummary.incrementAdded();
						} else {
							throw new ServiceException(format("Concept with code '%s' on row %s is from a different module and can not be modified using this function.",
								conceptCode, intent.getRowNumber()));
						}
					}

					if (intent.isInactive()) {
						if (concept.isActive()) {
							concept.setActive(false);// Snowstorm will delete this concept automatically if it has never been released.
							concept.getClassAxioms().forEach(axiom -> axiom.setActive(false));
							changed = true;
							changeSummary.incrementRemoved();
						}
					} else {
						// Intent is active
						if (!concept.isActive()) {
							// Reactivate concept
							concept.setActive(true);
							activateLatestSet(concept.getClassAxioms());
							activateLatestSet(concept.getRelationships());
							changed = true;
							changeSummary.incrementUpdated();
						}
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
							if (concept.getClassAxioms().isEmpty()) {
								concept.addAxiom(new Axiom());
							}
							Axiom axiom = concept.getClassAxioms().get(0);
							axiom.setEffectiveTime(null);
							axiom.setActive(true);
							axiom.setModuleId(defaultModule);
							axiom.setDefinitionStatus("PRIMITIVE");
							axiom.setRelationships(Collections.singletonList(Relationship.stated(IS_A, parentConcept.getConceptId(), 0)));
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
						String language = getLanguageCode(codeSystem, langRefsetId);
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
						String language = getLanguageCode(codeSystem, langRefsetId);
						List<Description> langRefsetRelevantDescriptions = descriptions.stream()
								.filter(description -> description.getAcceptabilityMap().containsKey(langRefsetId)).toList();
						boolean updateConceptDescriptions = translationService.updateConceptDescriptions(concept.getConceptId(),
								concept.getDescriptions(), new ArrayList<>(langRefsetRelevantDescriptions),
								language, langRefsetId, true, new DummyChangeMonitor(), new ChangeSummary());
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

	private void activateLatestSet(Collection<? extends Component>  components) {
		// Only activate the components with the latest effectiveTime within the set
		if (components.isEmpty()) {
			return;
		}
		TreeSet<String> dates = new TreeSet<>();
		boolean someNullDates = false;
		for (Component component : components) {
			if (component.getEffectiveTime() == null) {
				someNullDates = true;
				dates.add("_");
			} else {
				dates.add(component.getEffectiveTime());
			}
		}
		String latestDate = dates.descendingIterator().next();
		for (Component component : components) {
			String componentEffectiveTime = component.getEffectiveTime();
			if ((someNullDates && componentEffectiveTime == null) || (!someNullDates && latestDate.equals(componentEffectiveTime))) {
				if (!component.isActive()) {
					component.setActive(true);
					component.setEffectiveTime(null);
				}
			}
		}
	}

	private static String getLanguageCode(CodeSystem codeSystem, String langRefsetId) {
		return codeSystem.getTranslationLanguages().getOrDefault(langRefsetId, "en");
	}

	private void replaceNonBreakingSpaces(List<ConceptIntent> conceptIntents) {
		for (ConceptIntent conceptIntent : conceptIntents) {
			for (List<String> terms : conceptIntent.getLangRefsetTerms().values()) {
				ListIterator<String> listIterator = terms.listIterator();
				while (listIterator.hasNext()) {
					String term = listIterator.next();
					// Replace non-breaking spaces
					String newTerm = term.replaceAll("\\h", " ");
					if (!newTerm.equals(term)) {
						listIterator.set(newTerm);
					}
				}
			}
		}
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

	protected List<SheetHeader> getInputSheetHeaders(List<ConceptMini> langRefsets) {
		List<SheetHeader> headers = new ArrayList<>();
		headers.add(new SheetHeader("Parent Concept Identifier").setSubtitle("Only one can be given per concept."));
		headers.add(new SheetHeader("Parent Concept Term").setSubtitle("This is just for reference."));
		headers.add(new SheetHeader("Concept Identifier").setSubtitle("Do not change this column. \r\n" +
				"Concept identifiers will be generated."));
		headers.add(new SheetHeader("Active").setSubtitle("Leave this column blank to start with. \r\n" +
				"To make a concept inactive enter a value of false."));
		String termSubtitle = "The first term will be the preferred term. \r\n" +
				"Each additional synonym should use a new row. There is no need to repeat the values in columns A to D.";
		headers.add(new SheetHeader("Terms in English, US dialect. This term is required.").setSubtitle(termSubtitle));
		for (ConceptMini langRefset : langRefsets) {
			headers.add(new SheetHeader(format("Terms in %s (%s)", langRefset.getPt().getTerm(), langRefset.getConceptId())).setSubtitle(termSubtitle).optional());
		}
		return headers;
	}

	protected SheetRowToComponentIntentExtractor<ConceptIntent> getInputSheetComponentExtractor(final List<String> langRefsetIds) {
		return new ConceptIntentSheetRowToComponentIntentExtractor(langRefsetIds);
	}

	private static class ConceptIntentSheetRowToComponentIntentExtractor implements SheetRowToComponentIntentExtractor<ConceptIntent> {

		private static final String PARENT_CODE = "Parent Concept Identifier";
		private static final String CONCEPT_CODE = "Concept Identifier";
		private static final String ACTIVE = "Active";
		private static final String TERMS_EN_US = "Terms in English, US dialect";
		private final List<String> langRefsetIds;

		private ConceptIntent mainComponent;
		private boolean headerValidationComplete;
		private Map<Integer, String> termColumnIndexToLangRefsetMap;
		private final Pattern langRefsetIdPattern;

		public ConceptIntentSheetRowToComponentIntentExtractor(List<String> langRefsetIds) {
			this.langRefsetIds = langRefsetIds;
			termColumnIndexToLangRefsetMap = new HashMap<>();
			langRefsetIdPattern = Pattern.compile("Terms in .*\\((\\d+)\\).*");
		}

		@Override
		public ConceptIntent extract(Row row, Integer rowNumber, HeaderConfiguration headerConfiguration) throws ServiceException {
			if (!headerValidationComplete) {
				termColumnIndexToLangRefsetMap = getTermColumnIndexToLangRefsetMap(headerConfiguration);
				headerValidationComplete = true;
			}

			ConceptIntent conceptIntent;

			String parentCode = SpreadsheetService.readSnomedConcept(row, headerConfiguration.getColumn(PARENT_CODE), rowNumber);
			boolean conceptRow = parentCode != null;
			if (conceptRow) {
				conceptIntent = new ConceptIntent(parentCode, rowNumber);
				boolean inactive = row.getCell(headerConfiguration.getColumn(ACTIVE), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue().equalsIgnoreCase("false");
				conceptIntent.setInactive(inactive);
				mainComponent = conceptIntent;
				String conceptCode = SpreadsheetService.readSnomedConcept(row, headerConfiguration.getColumn(CONCEPT_CODE), rowNumber);
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
					String term = cell.getStringCellValue().trim();
					if (!term.isEmpty()) {
						conceptIntent.addTerm(term, entry.getValue());
					}
				}
			}
			if (conceptRow && conceptIntent.getLangRefsetTerms().getOrDefault(US_LANG_REFSET, Collections.emptyList()).isEmpty()) {
				throw new ServiceExceptionWithStatusCode("English (US Dialect) term missing from row %s.".formatted(rowNumber),
						HttpStatus.BAD_REQUEST, JobStatus.USER_CONTENT_ERROR);
			}

			// If there was no parent code then we updated the previous concept, don't return it again
			return conceptRow ? conceptIntent : null;
		}

		private Map<Integer, String> getTermColumnIndexToLangRefsetMap(HeaderConfiguration headerConfiguration) throws ServiceException {
			Map<Integer, String> map = new HashMap<>();

			Integer enUsIndex = headerConfiguration.getColumnStarting(TERMS_EN_US);
			if (enUsIndex == null) {
				throw new ServiceException(format("Mandatory column not found. Please ensure there is a column with title starting '%s'", TERMS_EN_US));
			}
			map.put(enUsIndex, Concepts.US_LANG_REFSET);

			List<Integer> languageHeaders = headerConfiguration.getColumnsMatching("Terms in .*\\)");
			for (Integer languageHeaderColumn : languageHeaders) {
				SheetHeader header = headerConfiguration.getHeader(languageHeaderColumn);
				String name = header.getName();
				Matcher matcher = langRefsetIdPattern.matcher(name);
				if (matcher.matches()) {
					String langRefsetId = matcher.group(1);
					if (langRefsetIds.contains(langRefsetId)) {
						map.put(languageHeaderColumn, langRefsetId);
					} else {
						throw new ServiceException(format("The language reference set code given in the header of column %s is not one of the expected values.",
								languageHeaderColumn + 1));
					}
				} else {
					throw new ServiceException(format("The header of column %s does not match the expected pattern for language reference sets.",
							languageHeaderColumn + 1));
				}
			}
			return map;
		}
	}
}
