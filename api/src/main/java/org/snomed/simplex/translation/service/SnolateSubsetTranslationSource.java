package org.snomed.simplex.translation.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.translation.domain.TranslationState;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Snolate translation state limited to concepts in a snolate translation set (subset).
 */
public class SnolateSubsetTranslationSource implements TranslationSource {

	private final SnolateTranslationSearchService translationSearchService;
	private final String compositeLanguageCode;
	private final String compositeSetCode;

	public SnolateSubsetTranslationSource(SnolateTranslationSearchService translationSearchService, String languageCode, String refsetId,
			String compositeSetCode) {
		this.translationSearchService = translationSearchService;
		this.compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		this.compositeSetCode = compositeSetCode;
	}

	@Override
	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		TranslationState state = new TranslationState();
		Map<Long, List<String>> conceptTerms = state.getConceptTerms();
		List<TranslationUnit> inSet = translationSearchService.listAllUnitsInSet(compositeSetCode, compositeLanguageCode);
		for (TranslationUnit unit : inSet) {
			try {
				long conceptId = Long.parseLong(unit.getCode());
				if (unit.hasTermContent()) {
					conceptTerms.put(conceptId, new ArrayList<>(unit.getTerms()));
				}
			} catch (NumberFormatException e) {
				throw new ServiceExceptionWithStatusCode(
						"Snolate translation unit has non-numeric code: %s".formatted(unit.getCode()),
						HttpStatus.INTERNAL_SERVER_ERROR, e);
			}
		}
		return state;
	}

	@Override
	public void writeTranslation(TranslationState translationState) {
		throw new UnsupportedOperationException();
	}

	@Override
	public TranslationSourceType getType() {
		return TranslationSourceType.SNOLATE_SUBSET;
	}
}
