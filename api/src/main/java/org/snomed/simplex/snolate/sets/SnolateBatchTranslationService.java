package org.snomed.simplex.snolate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.BatchTranslateRequest;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.BatchTranslationPrompt;
import org.snomed.simplex.translation.TranslationLLMService;

import java.util.*;
import java.util.stream.StreamSupport;

import static org.snomed.simplex.snolate.sets.SnolateSetService.JOB_TYPE_BATCH_AI_TRANSLATE;
import static org.snomed.simplex.snolate.sets.SnolateSetService.PERCENTAGE_PROCESSED_START;

public class SnolateBatchTranslationService extends AbstractSnolateSetProcessingService {

	public static final int MAX_PAGE_SIZE = 50;

	/** Chunk size for Elasticsearch batch reads in {@link #loadSourcesByCodes}. */
	private static final int ELASTIC_IO_CHUNK_SIZE = 1_000;
	private final TranslationLLMService translationLLMService;
	private final SnolateTranslationUnitRepository translationUnitRepository;
	private final SnolateTranslationSourceRepository translationSourceRepository;
	private final SnolateTranslationSearchService translationSearchService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnolateBatchTranslationService(SnolateProcessingContext processingContext) {
		super(processingContext);
		this.translationLLMService = processingContext.translationLLMService();
		this.translationUnitRepository = processingContext.translationUnitRepository();
		this.translationSourceRepository = processingContext.translationSourceRepository();
		this.translationSearchService = processingContext.translationSearchService();
	}

	public void runAiBatchTranslate(SnolateTranslationSet translationSet, BatchTranslateRequest request) throws ServiceException {
		queueJob(translationSet, JOB_TYPE_BATCH_AI_TRANSLATE, request);
	}

	public void doRunAiBatchTranslate(SnolateTranslationSet translationSet, BatchTranslateRequest request) throws ServiceException {
		setProgress(translationSet, PERCENTAGE_PROCESSED_START);
		int requestedTotal = request.size();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		String setCode = translationSet.getCompositeSetCode();
		int progressPercent = PERCENTAGE_PROCESSED_START;
		int unitsProcessed = 0;

		while (unitsProcessed < requestedTotal) {
			int batchCap = Math.min(MAX_PAGE_SIZE, requestedTotal - unitsProcessed);

			// Iteratively fetch units that do not yet have suggestions
			List<TranslationUnit> batchUnits = translationSearchService.listEligibleUnitsForBatchTranslate(setCode, lang, batchCap);
			if (batchUnits.isEmpty()) {
				break;
			}
			Map<String, List<TranslationUnit>> contextByCode = loadNeighbourContextByCode(setCode, lang, batchUnits);
			Map<String, TranslationSource> sourcesByCode = loadSourcesByCodes(collectRequiredSourceCodes(batchUnits, contextByCode));
			BatchTranslationPrompt prompt = buildBatchPrompt(batchUnits, sourcesByCode, contextByCode);
			if (prompt.translateLineNumbers().isEmpty()) {
				break;
			}
			Map<String, List<String>> suggestions = translationLLMService.suggestBatchTranslations(translationSet, prompt);
			progressPercent = Math.min(99, progressPercent + 10);
			setProgress(translationSet, progressPercent);

			persistSuggestions(translationSet, batchUnits, sourcesByCode, suggestions, lang, setCode);
			unitsProcessed += batchUnits.size();
		}
		if (unitsProcessed == 0) {
			logger.info("No more empty Snolate units in set {}", setCode);
		}
		setProgressToComplete(translationSet);
	}

	private Map<String, List<TranslationUnit>> loadNeighbourContextByCode(String setCode, String lang, List<TranslationUnit> batchUnits) {
		Map<String, List<TranslationUnit>> contextByCode = new HashMap<>();
		for (TranslationUnit unit : batchUnits) {
			contextByCode.put(unit.getCode(),
					translationSearchService.findAcceptedContextUnitsBeforeOrder(setCode, lang, unit.getOrder()));
		}
		return contextByCode;
	}

	private void persistSuggestions(SnolateTranslationSet translationSet, List<TranslationUnit> batchUnits,
			Map<String, TranslationSource> sourcesByCode, Map<String, List<String>> suggestions, String lang, String setCode) {
		for (TranslationUnit unit : batchUnits) {
			TranslationSource src = sourcesByCode.get(unit.getCode());
			if (src == null) {
				continue;
			}
			List<String> sug = suggestions.get(src.getTerm());
			if (sug == null || sug.isEmpty()) {
				continue;
			}
			String suggestion = sug.get(0);
			Optional<TranslationUnit> opt = translationUnitRepository.findByCodeAndCompositeLanguageCode(src.getCode(), lang);
			if (opt.isPresent()) {
				TranslationUnit u = opt.get();
				u.setAiSuggestions(new ArrayList<>(List.of(suggestion)));
				translationUnitRepository.save(u);
			} else {
				TranslationUnit u = new TranslationUnit(
						new TranslationUnit.MembershipKey(src.getCode(), translationSet.getRefset(), translationSet.getLanguageCode(), lang, src.getOrder()),
						new ArrayList<>(), TranslationStatus.NOT_STARTED, new LinkedHashSet<>(List.of(setCode)));
				u.setAiSuggestions(new ArrayList<>(List.of(suggestion)));
				translationUnitRepository.save(u);
			}
		}
	}

	static BatchTranslationPrompt buildBatchPrompt(List<TranslationUnit> batchUnits,
			Map<String, TranslationSource> sourcesByCode, Map<String, List<TranslationUnit>> contextByCode) {
		BatchTranslationPrompt.Builder builder = BatchTranslationPrompt.builder();
		Set<String> includedContextCodes = new HashSet<>();
		for (TranslationUnit unit : batchUnits) {
			for (TranslationUnit contextUnit : contextByCode.getOrDefault(unit.getCode(), List.of())) {
				if (!includedContextCodes.add(contextUnit.getCode())) {
					continue;
				}
				TranslationSource contextSource = sourcesByCode.get(contextUnit.getCode());
				if (contextSource == null || contextUnit.getTerms().isEmpty()) {
					continue;
				}
				builder.addContextLine(contextSource.getTerm(), contextUnit.getTerms().get(0));
			}
			TranslationSource source = sourcesByCode.get(unit.getCode());
			if (source != null) {
				builder.addTranslateLine(source.getTerm());
			}
		}
		return builder.build();
	}

	static Set<String> collectRequiredSourceCodes(List<TranslationUnit> batchUnits,
			Map<String, List<TranslationUnit>> contextByCode) {
		Set<String> codes = new HashSet<>();
		for (TranslationUnit unit : batchUnits) {
			codes.add(unit.getCode());
			for (TranslationUnit contextUnit : contextByCode.getOrDefault(unit.getCode(), List.of())) {
				codes.add(contextUnit.getCode());
			}
		}
		return codes;
	}

	private Map<String, TranslationSource> loadSourcesByCodes(Collection<String> codes) {
		if (codes.isEmpty()) {
			return Map.of();
		}
		List<String> codeList = codes instanceof List<String> list ? list : new ArrayList<>(codes);
		Map<String, TranslationSource> sourcesByCode = new HashMap<>();
		for (int i = 0; i < codeList.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, codeList.size());
			StreamSupport.stream(translationSourceRepository.findAllById(codeList.subList(i, end)).spliterator(), false)
					.forEach(source -> sourcesByCode.put(source.getCode(), source));
		}
		return sourcesByCode;
	}
}
