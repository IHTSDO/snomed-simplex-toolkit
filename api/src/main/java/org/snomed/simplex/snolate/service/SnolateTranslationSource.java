package org.snomed.simplex.snolate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationStateDiff;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads and updates Snolate {@link TranslationUnit} documents for translation sync.
 */
public class SnolateTranslationSource {

	private static final Logger logger = LoggerFactory.getLogger(SnolateTranslationSource.class);

	private static final int WRITE_LOAD_BATCH_SIZE = 500;
	private static final int WRITE_SAVE_BATCH_SIZE = 5_000;

	private final SnolateTranslationUnitStore translationUnitStore;
	private final SnolateTranslationSearchService translationSearchService;
	private final String compositeLanguageCode;

	public SnolateTranslationSource(SnolateTranslationUnitStore translationUnitStore,
			SnolateTranslationSearchService translationSearchService,
			String languageCode, String refsetId) {
		this.translationUnitStore = translationUnitStore;
		this.translationSearchService = translationSearchService;
		this.compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
	}

	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		TranslationState state = new TranslationState();
		Map<Long, List<String>> conceptTerms = state.getConceptTerms();
		AtomicReference<ServiceExceptionWithStatusCode> readFailure = new AtomicReference<>();
		translationSearchService.forEachUnitByCompositeLanguageCode(compositeLanguageCode, unit -> {
			if (readFailure.get() != null) {
				return;
			}
			try {
				conceptTerms.put(Long.parseLong(unit.getCode()), new ArrayList<>(unit.getTerms()));
			} catch (NumberFormatException e) {
				readFailure.set(new ServiceExceptionWithStatusCode(
						"Snolate translation unit has non-numeric code: %s".formatted(unit.getCode()),
						HttpStatus.INTERNAL_SERVER_ERROR, e));
			}
		});
		if (readFailure.get() != null) {
			throw readFailure.get();
		}
		return state;
	}

	/**
	 * Apply Snowstorm delta ADD/REMOVE to existing units (language-wide). No-op if unit does not exist.
	 */
	public void applyDelta(TranslationIntent delta) {
		List<Map.Entry<Long, List<TermIntent>>> entries = new ArrayList<>(delta.getTermIntents().entrySet());
		List<TranslationUnit> saveBuffer = new ArrayList<>();
		AtomicInteger savedTotal = new AtomicInteger();

		for (int i = 0; i < entries.size(); i += WRITE_LOAD_BATCH_SIZE) {
			int end = Math.min(i + WRITE_LOAD_BATCH_SIZE, entries.size());
			List<Map.Entry<Long, List<TermIntent>>> chunk = entries.subList(i, end);
			List<String> codes = chunk.stream().map(e -> e.getKey().toString()).toList();
			Map<String, TranslationUnit> byCode = translationUnitStore.loadByCodes(compositeLanguageCode, codes);

			for (Map.Entry<Long, List<TermIntent>> entry : chunk) {
				String code = entry.getKey().toString();
				List<TermIntent> termIntents = entry.getValue();
				if (!hasAdditionsOrRemovals(termIntents)) {
					continue;
				}
				TranslationUnit unit = byCode.get(code);
				if (unit == null) {
					continue;
				}
				List<String> updatedTerms = TranslationStateDiff.applyIntent(unit.getTerms(), termIntents);
				if (!updatedTerms.equals(unit.getTerms())) {
					unit.setTerms(updatedTerms);
					saveBuffer.add(unit);
					flushSaveBufferIfNeeded(saveBuffer, savedTotal);
				}
			}
		}
		flushSaveBufferRemainder(saveBuffer, savedTotal);
	}

	private static boolean hasAdditionsOrRemovals(List<TermIntent> termIntents) {
		return termIntents.stream().anyMatch(ti -> ti.intent() == Intent.ADD || ti.intent() == Intent.REMOVE);
	}

	private void flushSaveBufferIfNeeded(List<TranslationUnit> saveBuffer, AtomicInteger savedTotal) {
		while (saveBuffer.size() >= WRITE_SAVE_BATCH_SIZE) {
			translationUnitStore.saveAll(saveBuffer.subList(0, WRITE_SAVE_BATCH_SIZE));
			saveBuffer.subList(0, WRITE_SAVE_BATCH_SIZE).clear();
			logSaveBatch(WRITE_SAVE_BATCH_SIZE, savedTotal);
		}
	}

	private void flushSaveBufferRemainder(List<TranslationUnit> saveBuffer, AtomicInteger savedTotal) {
		if (!saveBuffer.isEmpty()) {
			int n = saveBuffer.size();
			translationUnitStore.saveAll(saveBuffer);
			logSaveBatch(n, savedTotal);
		}
	}

	private void logSaveBatch(int batchSize, AtomicInteger savedTotal) {
		int total = savedTotal.addAndGet(batchSize);
		logger.info("Saved batch of {} translation units {} ({} so far).",
				batchSize, compositeLanguageCode, total);
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

}
