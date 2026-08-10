package org.snomed.simplex.translation.service;

import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * High level service to plan Snowstorm ↔ Snolate sync using a single Snowstorm snapshot baseline.
 */
@Service
public class TranslationSyncService {

	public record PushPlan(
			TranslationState snowstormUpload,
			TranslationState postPushSnapshot,
			boolean hasSnowstormUpload) {
	}

	public PushPlan planPushUpload(TranslationState previousSnowstorm, TranslationState currentSnowstorm,
			TranslationIntent snowstormDelta, TranslationState snolateSubset, Set<Long> conceptIds) {

		boolean blankPrevious = previousSnowstorm.getConceptTerms().isEmpty();
		TranslationIntent classificationDelta = blankPrevious ? new TranslationIntent() : snowstormDelta;

		TranslationState snowstormUpload = new TranslationState();
		TranslationState postPushSnapshot = TranslationStateDiff.copy(currentSnowstorm);

		for (Long conceptId : conceptIds) {
			List<String> snolateTerms = TranslationStateDiff.termsForConcept(snolateSubset, conceptId);
			List<String> currentSnowstormTerms = TranslationStateDiff.termsForConcept(currentSnowstorm, conceptId);
			List<String> targetTerms = buildTargetTerms(snolateTerms, currentSnowstormTerms, classificationDelta, conceptId);

			if (!targetTerms.equals(currentSnowstormTerms)) {
				snowstormUpload.getConceptTerms().put(conceptId, targetTerms);
				postPushSnapshot.getConceptTerms().put(conceptId, targetTerms);
			}
		}

		return new PushPlan(snowstormUpload, postPushSnapshot, !snowstormUpload.getConceptTerms().isEmpty());
	}

	/**
	 * Snolate term order wins, plus Snowstorm-ahead terms (delta ADD, not already in Snolate).
	 * Stable Snowstorm-only terms omitted (Snolate-side removal).
	 */
	static List<String> buildTargetTerms(List<String> snolateTerms, List<String> currentSnowstormTerms,
			TranslationIntent classificationDelta, Long conceptId) {

		List<String> target = new ArrayList<>(snolateTerms);
		for (String snowstormTerm : currentSnowstormTerms) {
			if (!target.contains(snowstormTerm)
					&& TranslationStateDiff.isSnowstormAdd(classificationDelta, conceptId, snowstormTerm)) {
				target.add(snowstormTerm);
			}
		}
		return target;
	}

}
