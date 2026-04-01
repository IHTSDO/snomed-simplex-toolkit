package org.snomed.simplex.snolate.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.domain.TranslationUnitId;
import org.snomed.simplex.snolate.repository.TranslationUnitRepository;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationSource;
import org.snomed.simplex.translation.service.TranslationSourceType;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Snolate persistence backing for {@link TranslationSource}. Maps JPA {@link TranslationUnit} rows to
 * {@link TranslationState} for {@link org.snomed.simplex.translation.service.TranslationMergeService}.
 * <p>
 * {@link #writeTranslation} receives <strong>additions-only</strong> batches (same contract as Weblate).
 * New terms are merged without duplicates: if the unit has no terms yet, the first new term is stored
 * first; otherwise each new term is appended. This mirrors {@code TranslationMergeService.applyIntent}
 * when the target already holds terms (preferred slot taken). Snowstorm-driven removals are not applied here.
 */
public class SnolateTranslationSource implements TranslationSource {

	private final TranslationUnitRepository translationUnitRepository;
	private final String compositeLanguageCode;

	public SnolateTranslationSource(TranslationUnitRepository translationUnitRepository, String languageCode, String refsetId) {
		this.translationUnitRepository = translationUnitRepository;
		this.compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
	}

	@Override
	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		TranslationState state = new TranslationState();
		Map<Long, List<String>> conceptTerms = state.getConceptTerms();
		for (TranslationUnit unit : translationUnitRepository.findAllByLanguageCode(compositeLanguageCode)) {
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
		for (Map.Entry<Long, List<String>> entry : translationState.getConceptTerms().entrySet()) {
			String code = entry.getKey().toString();
			List<String> additions = entry.getValue();
			TranslationUnitId id = new TranslationUnitId(code, compositeLanguageCode);
			translationUnitRepository.findById(id)
					.map(unit -> {
						unit.setTerms(mergeAdditions(unit.getTerms(), additions));
						return translationUnitRepository.save(unit);
					})
					.orElseGet(() -> translationUnitRepository.save(
							new TranslationUnit(code, compositeLanguageCode, new ArrayList<>(additions), TranslationStatus.APPROVED)));
		}
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
