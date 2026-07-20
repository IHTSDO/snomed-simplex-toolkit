package org.snomed.simplex.snolate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.BatchTranslateRequest;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationStatuses;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.BatchTranslationPrompt;
import org.snomed.simplex.translation.TranslationLLMService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.snomed.simplex.snolate.sets.SnolateSetService.JOB_TYPE_BATCH_AI_TRANSLATE;
import static org.snomed.simplex.snolate.sets.SnolateSetService.PERCENTAGE_PROCESSED_START;

public class SnolateBatchTranslationService extends AbstractSnolateSetProcessingService {

	public static final int MAX_PAGE_SIZE = 50;
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

	public void doRunAiBatchTranslate(SnolateTranslationSet translationSet, BatchTranslateRequest request) {
		setProgress(translationSet, PERCENTAGE_PROCESSED_START);
		int requestedTotal = request.size();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		String setCode = translationSet.getCompositeSetCode();
		int progressPercent = PERCENTAGE_PROCESSED_START;

		List<TranslationUnit> orderedUnits = translationSearchService.listAllUnitsInSet(setCode, lang);
		Map<String, TranslationSource> sourcesByCode = loadSourcesByCode(orderedUnits);
		List<Integer> eligibleIndices = findEligibleIndices(orderedUnits);
		int unitsProcessed = 0;
		int eligibleOffset = 0;

		while (unitsProcessed < requestedTotal && eligibleOffset < eligibleIndices.size()) {
			int batchCap = Math.min(MAX_PAGE_SIZE, requestedTotal - unitsProcessed);
			int batchEnd = Math.min(eligibleOffset + batchCap, eligibleIndices.size());
			List<Integer> batchIndices = eligibleIndices.subList(eligibleOffset, batchEnd);
			BatchTranslationPrompt prompt = buildBatchPrompt(orderedUnits, batchIndices, sourcesByCode);
			if (prompt.translateLineNumbers().isEmpty()) {
				break;
			}
			Map<String, List<String>> suggestions = translationLLMService.suggestBatchTranslations(translationSet, prompt);
			progressPercent = Math.min(99, progressPercent + 10);
			setProgress(translationSet, progressPercent);

			persistSuggestions(translationSet, batchIndices, orderedUnits, sourcesByCode, suggestions, lang, setCode);
			unitsProcessed += batchIndices.size();
			eligibleOffset = batchEnd;
		}
		if (unitsProcessed == 0) {
			logger.info("No more empty Snolate units in set {}", setCode);
		}
		setProgressToComplete(translationSet);
	}

	private void persistSuggestions(SnolateTranslationSet translationSet, List<Integer> batchIndices, List<TranslationUnit> orderedUnits, Map<String, TranslationSource> sourcesByCode, Map<String, List<String>> suggestions, String lang, String setCode) {
		for (int index : batchIndices) {
			TranslationUnit unit = orderedUnits.get(index);
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

	static List<Integer> findEligibleIndices(List<TranslationUnit> orderedUnits) {
		List<Integer> eligible = new ArrayList<>();
		for (int i = 0; i < orderedUnits.size(); i++) {
			TranslationUnit unit = orderedUnits.get(i);
			if (!unit.hasTermContent() && !unit.hasAiSuggestions()) {
				eligible.add(i);
			}
		}
		return eligible;
	}

	static List<TranslationUnit> findContextUnits(List<TranslationUnit> ordered, int targetIndex) {
		List<TranslationUnit> found = new ArrayList<>();
		for (int j = targetIndex - 1; j >= 0 && found.size() < 2; j--) {
			TranslationUnit candidate = ordered.get(j);
			if (TranslationStatuses.isAcceptedContext(candidate)) {
				found.add(candidate);
			}
		}
		Collections.reverse(found);
		return found;
	}

	static BatchTranslationPrompt buildBatchPrompt(List<TranslationUnit> orderedUnits, List<Integer> batchIndices,
			Map<String, TranslationSource> sourcesByCode) {
		BatchTranslationPrompt.Builder builder = BatchTranslationPrompt.builder();
		Set<String> includedContextCodes = new HashSet<>();
		for (int index : batchIndices) {
			for (TranslationUnit contextUnit : findContextUnits(orderedUnits, index)) {
				if (!includedContextCodes.add(contextUnit.getCode())) {
					continue;
				}
				TranslationSource contextSource = sourcesByCode.get(contextUnit.getCode());
				if (contextSource == null || contextUnit.getTerms().isEmpty()) {
					continue;
				}
				builder.addContextLine(contextSource.getTerm(), contextUnit.getTerms().get(0));
			}
			TranslationUnit unit = orderedUnits.get(index);
			TranslationSource source = sourcesByCode.get(unit.getCode());
			if (source != null) {
				builder.addTranslateLine(source.getTerm());
			}
		}
		return builder.build();
	}

	private Map<String, TranslationSource> loadSourcesByCode(List<TranslationUnit> orderedUnits) {
		List<String> codes = orderedUnits.stream().map(TranslationUnit::getCode).distinct().toList();
		if (codes.isEmpty()) {
			return Map.of();
		}
		return StreamSupport.stream(translationSourceRepository.findAllById(codes).spliterator(), false)
				.collect(Collectors.toMap(TranslationSource::getCode, s -> s, (a, b) -> a));
	}
}
