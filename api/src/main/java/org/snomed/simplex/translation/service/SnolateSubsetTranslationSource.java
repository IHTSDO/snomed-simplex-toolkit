package org.snomed.simplex.translation.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.domain.TranslationUnitId;
import org.snomed.simplex.snolate.repository.TranslationSourceRepository;
import org.snomed.simplex.snolate.repository.TranslationUnitRepository;
import org.snomed.simplex.translation.domain.TranslationState;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Snolate translation state limited to concepts in a snolate translation set (subset).
 */
public class SnolateSubsetTranslationSource implements TranslationSource {

	private final TranslationUnitRepository translationUnitRepository;
	private final TranslationSourceRepository translationSourceRepository;
	private final String compositeLanguageCode;
	private final String compositeSetCode;

	public SnolateSubsetTranslationSource(TranslationUnitRepository translationUnitRepository,
			TranslationSourceRepository translationSourceRepository, String languageCode, String refsetId, String compositeSetCode) {
		this.translationUnitRepository = translationUnitRepository;
		this.translationSourceRepository = translationSourceRepository;
		this.compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		this.compositeSetCode = compositeSetCode;
	}

	@Override
	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		TranslationState state = new TranslationState();
		Map<Long, List<String>> conceptTerms = state.getConceptTerms();
		List<org.snomed.simplex.snolate.domain.TranslationSource> inSet = translationSourceRepository.findAllHavingSetMembership(compositeSetCode);
		for (org.snomed.simplex.snolate.domain.TranslationSource sourceRow : inSet) {
			try {
				long conceptId = Long.parseLong(sourceRow.getCode());
				Optional<TranslationUnit> unit = translationUnitRepository.findById(new TranslationUnitId(sourceRow.getCode(), compositeLanguageCode));
				if (unit.isPresent() && !unit.get().getTerms().isEmpty()) {
					conceptTerms.put(conceptId, new ArrayList<>(unit.get().getTerms()));
				}
			} catch (NumberFormatException e) {
				throw new ServiceExceptionWithStatusCode(
						"Snolate translation source has non-numeric code: %s".formatted(sourceRow.getCode()),
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
