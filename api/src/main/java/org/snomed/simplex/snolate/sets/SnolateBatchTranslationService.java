package org.snomed.simplex.snolate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.BatchTranslateRequest;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.TranslationLLMService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

	public void doRunAiBatchTranslate(SnolateTranslationSet translationSet, BatchTranslateRequest request) throws ServiceException {
		setProgress(translationSet, PERCENTAGE_PROCESSED_START);
		int requestedTotal = request.size();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		String setCode = translationSet.getCompositeSetCode();
		int progressPercent = PERCENTAGE_PROCESSED_START;
		int unitsProcessed = 0;

		while (unitsProcessed < requestedTotal) {
			int batchCap = Math.min(MAX_PAGE_SIZE, requestedTotal - unitsProcessed);
			List<TranslationSource> batchSources = collectEmptySources(translationSet, setCode, lang, batchCap);
			if (batchSources.isEmpty()) {
				logger.info("No more empty Snolate units in set {}", setCode);
				break;
			}
			List<String> englishTerms = batchSources.stream().map(TranslationSource::getTerm).toList();
			Map<String, List<String>> suggestions = translationLLMService.suggestTranslations(translationSet, englishTerms, false, false);
			progressPercent = Math.min(99, progressPercent + 10);
			setProgress(translationSet, progressPercent);

			for (TranslationSource src : batchSources) {
				List<String> sug = suggestions.get(src.getTerm());
				if (sug == null || sug.isEmpty()) {
					continue;
				}
				String suggestion = sug.get(0);
				Optional<TranslationUnit> opt = translationUnitRepository.findByCodeAndCompositeLanguageCode(src.getCode(), lang);
				if (opt.isPresent()) {
					TranslationUnit u = opt.get();
					u.setTerms(new ArrayList<>(List.of(suggestion)));
					u.setStatus(TranslationStatus.FOR_REVIEW);
					translationUnitRepository.save(u);
				} else {
					TranslationUnit u = new TranslationUnit(src.getCode(), translationSet.getRefset(), translationSet.getLanguageCode(), lang, src.getOrder(),
							new ArrayList<>(List.of(suggestion)), TranslationStatus.FOR_REVIEW, new LinkedHashSet<>(List.of(setCode)));
					translationUnitRepository.save(u);
				}
			}
			unitsProcessed += batchSources.size();
		}
		setProgressToComplete(translationSet);
	}

	private List<TranslationSource> collectEmptySources(SnolateTranslationSet translationSet, String setCode, String lang, int batchCap) {
		List<TranslationSource> batchSources = new ArrayList<>();
		int pageNumber = 0;
		final int pageSize = 500;
		while (batchSources.size() < batchCap) {
			Page<TranslationUnit> page = translationSearchService.pageUnitsInSet(setCode, lang,
					PageRequest.of(pageNumber++, pageSize, Sort.by("code")));
			if (page.isEmpty()) {
				break;
			}
			for (TranslationUnit u : page.getContent()) {
				if (batchSources.size() >= batchCap) {
					break;
				}
				if (!u.hasTermContent()) {
					translationSourceRepository.findById(u.getCode()).ifPresent(batchSources::add);
				}
			}
			if (!page.hasNext()) {
				break;
			}
		}
		return batchSources;
	}
}
