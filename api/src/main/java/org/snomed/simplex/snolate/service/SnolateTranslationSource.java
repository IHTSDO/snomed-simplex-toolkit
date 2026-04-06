package org.snomed.simplex.snolate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationSource;
import org.snomed.simplex.translation.service.TranslationSourceType;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Snolate persistence backing for {@link TranslationSource}. Maps {@link TranslationUnit} documents to
 * {@link TranslationState} for {@link org.snomed.simplex.translation.service.TranslationMergeService}.
 */
public class SnolateTranslationSource implements TranslationSource {

	private static final Logger logger = LoggerFactory.getLogger(SnolateTranslationSource.class);

	private static final int WRITE_LOAD_BATCH_SIZE = 500;
	private static final int WRITE_SAVE_BATCH_SIZE = 5_000;

	private final SnolateTranslationUnitRepository translationUnitRepository;
	private final String isoLanguageCode;
	private final String refsetId;
	private final String compositeLanguageCode;

	public SnolateTranslationSource(SnolateTranslationUnitRepository translationUnitRepository, String languageCode, String refsetId) {
		this.translationUnitRepository = translationUnitRepository;
		this.isoLanguageCode = languageCode;
		this.refsetId = refsetId;
		this.compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
	}

	@Override
	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		TranslationState state = new TranslationState();
		Map<Long, List<String>> conceptTerms = state.getConceptTerms();
		for (TranslationUnit unit : translationUnitRepository.findAllByCompositeLanguageCode(compositeLanguageCode)) {
			try {
				conceptTerms.put(Long.parseLong(unit.getCode()), new ArrayList<>(unit.getTerms()));
			} catch (NumberFormatException e) {
				throw new ServiceExceptionWithStatusCode(
						"Snolate translation unit has non-numeric code: %s".formatted(unit.getCode()),
						HttpStatus.INTERNAL_SERVER_ERROR, e);
			}
		}
		return state;
	}

	@Override
	public void writeTranslation(TranslationState translationState) throws ServiceExceptionWithStatusCode {
		List<Map.Entry<Long, List<String>>> entries = new ArrayList<>(translationState.getConceptTerms().entrySet());
		List<TranslationUnit> saveBuffer = new ArrayList<>();
		AtomicInteger savedTotal = new AtomicInteger();

		for (int i = 0; i < entries.size(); i += WRITE_LOAD_BATCH_SIZE) {
			int end = Math.min(i + WRITE_LOAD_BATCH_SIZE, entries.size());
			List<Map.Entry<Long, List<String>>> chunk = entries.subList(i, end);
			List<String> codes = chunk.stream().map(e -> e.getKey().toString()).toList();
			List<TranslationUnit> existingBatch = translationUnitRepository.findAllByCompositeLanguageCodeAndCodeIn(
					compositeLanguageCode, codes);
			Map<String, TranslationUnit> byCode = existingBatch.stream()
					.collect(Collectors.toMap(TranslationUnit::getCode, Function.identity(), (a, b) -> a));

			for (Map.Entry<Long, List<String>> entry : chunk) {
				String code = entry.getKey().toString();
				List<String> additions = entry.getValue();
				TranslationUnit unit = byCode.get(code);
				if (unit != null) {
					unit.setTerms(mergeAdditions(unit.getTerms(), additions));
					saveBuffer.add(unit);
				} else {
					saveBuffer.add(newFullUnit(code, additions));
				}
				flushSaveBufferIfNeeded(saveBuffer, savedTotal);
			}
		}
		flushSaveBufferRemainder(saveBuffer, savedTotal);
	}

	private void flushSaveBufferIfNeeded(List<TranslationUnit> saveBuffer, AtomicInteger savedTotal) {
		while (saveBuffer.size() >= WRITE_SAVE_BATCH_SIZE) {
			translationUnitRepository.saveAll(saveBuffer.subList(0, WRITE_SAVE_BATCH_SIZE));
			saveBuffer.subList(0, WRITE_SAVE_BATCH_SIZE).clear();
			logSaveBatch(WRITE_SAVE_BATCH_SIZE, savedTotal);
		}
	}

	private void flushSaveBufferRemainder(List<TranslationUnit> saveBuffer, AtomicInteger savedTotal) {
		if (!saveBuffer.isEmpty()) {
			int n = saveBuffer.size();
			translationUnitRepository.saveAll(saveBuffer);
			logSaveBatch(n, savedTotal);
		}
	}

	private void logSaveBatch(int batchSize, AtomicInteger savedTotal) {
		int total = savedTotal.addAndGet(batchSize);
		logger.info("Saved batch of {} translation units {} ({} so far).",
				batchSize, compositeLanguageCode, total);
	}

	private TranslationUnit newFullUnit(String code, List<String> additions) {
		TranslationUnit u = new TranslationUnit(code, compositeLanguageCode, new ArrayList<>(additions), TranslationStatus.APPROVED);
		u.setRefsetId(refsetId);
		u.setLanguageCode(isoLanguageCode);
		u.setOrder(0);
		return u;
	}

	/**
	 * Merge addition-only terms into the existing list (dedupe, preserve rough ADD ordering from merge).
	 */
	static List<String> mergeAdditions(List<String> existing, List<String> additions) {
		List<String> merged = new ArrayList<>(existing);
		boolean ptFound = !merged.isEmpty();
		for (String t : additions) {
			if (merged.contains(t)) {
				continue;
			}
			if (!ptFound) {
				merged.add(0, t);
				ptFound = true;
			} else {
				merged.add(t);
			}
		}
		return merged;
	}

	@Override
	public TranslationSourceType getType() {
		return TranslationSourceType.SNOLATE;
	}
}
