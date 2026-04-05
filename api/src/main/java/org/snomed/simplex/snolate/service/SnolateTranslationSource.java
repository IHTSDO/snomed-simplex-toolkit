package org.snomed.simplex.snolate.service;

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

/**
 * Snolate persistence backing for {@link TranslationSource}. Maps {@link TranslationUnit} documents to
 * {@link TranslationState} for {@link org.snomed.simplex.translation.service.TranslationMergeService}.
 */
public class SnolateTranslationSource implements TranslationSource {

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
		for (Map.Entry<Long, List<String>> entry : translationState.getConceptTerms().entrySet()) {
			String code = entry.getKey().toString();
			List<String> additions = entry.getValue();
			translationUnitRepository.findByCodeAndCompositeLanguageCode(code, compositeLanguageCode)
					.map(unit -> {
						unit.setTerms(mergeAdditions(unit.getTerms(), additions));
						return translationUnitRepository.save(unit);
					})
					.orElseGet(() -> translationUnitRepository.save(newFullUnit(code, additions)));
		}
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
