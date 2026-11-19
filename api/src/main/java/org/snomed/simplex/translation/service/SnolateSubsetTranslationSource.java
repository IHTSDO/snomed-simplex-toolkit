package org.snomed.simplex.translation.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.translation.domain.TranslationState;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only Snolate translation state for concepts in a translation set.
 */
public class SnolateSubsetTranslationSource {

	private final SnolateTranslationSearchService translationSearchService;
	private final String compositeLanguageCode;
	private final String compositeSetCode;
	private final Set<TranslationStatus> includedStatuses;

	public SnolateSubsetTranslationSource(SnolateTranslationSearchService translationSearchService, String languageCode, String refsetId,
			String compositeSetCode, boolean includeReadyForReview) {
		this(translationSearchService, languageCode, refsetId, compositeSetCode, statusesForReadyForReviewFlag(includeReadyForReview));
	}

	public SnolateSubsetTranslationSource(SnolateTranslationSearchService translationSearchService, String languageCode, String refsetId,
			String compositeSetCode, Set<TranslationStatus> includedStatuses) {
		this.translationSearchService = translationSearchService;
		this.compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		this.compositeSetCode = compositeSetCode;
		this.includedStatuses = EnumSet.noneOf(TranslationStatus.class);
		this.includedStatuses.addAll(includedStatuses);
	}

	/**
	 * Legacy rule: every status is included, optionally excluding {@link TranslationStatus#FOR_REVIEW}.
	 */
	public static Set<TranslationStatus> statusesForReadyForReviewFlag(boolean includeReadyForReview) {
		EnumSet<TranslationStatus> statuses = EnumSet.allOf(TranslationStatus.class);
		if (!includeReadyForReview) {
			statuses.remove(TranslationStatus.FOR_REVIEW);
		}
		return statuses;
	}

	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		TranslationState state = new TranslationState();
		Map<Long, List<String>> conceptTerms = state.getConceptTerms();
		List<TranslationUnit> inSet = translationSearchService.listAllUnitsInSet(compositeSetCode, compositeLanguageCode);
		for (TranslationUnit unit : inSet) {
			try {
				long conceptId = Long.parseLong(unit.getCode());
				if (unit.hasTermContent() && shouldIncludeUnit(unit)) {
					conceptTerms.put(conceptId, new ArrayList<>(unit.getTerms()));
				}
			} catch (NumberFormatException e) {
				throw new ServiceExceptionWithStatusCode(
						"Snolate translation unit has non-numeric code: %s".formatted(unit.getCode()),
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
		return state;
	}

	private boolean shouldIncludeUnit(TranslationUnit unit) {
		TranslationStatus status = unit.getStatus() != null ? unit.getStatus() : TranslationStatus.NOT_STARTED;
		return includedStatuses.contains(status);
	}
}
