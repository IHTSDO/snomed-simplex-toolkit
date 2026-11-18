package org.snomed.simplex.translation.service;

import org.snomed.simplex.translation.domain.TranslationState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves concurrent changes on both sides of a Managed Service {@code REBASE}: content that flowed into the
 * translation project branch from elsewhere at the code-system level ("upstream"), versus content edited in
 * Translation Studio/Snolate but not yet pushed ("local"). Both are compared against the last-synced-from-Snowstorm
 * baseline, the same 3-way comparison already used by {@link TranslationStateDiff} for the push direction
 * ({@link TranslationSyncService}).
 * <p>
 * Decided policy (2026-11-17): auto-merge everything, no manual
 * resolution. Per concept:
 * <ul>
 *   <li>Only one side changed the concept (relative to baseline) &rarr; that side's terms win. Not a conflict.</li>
 *   <li>Both sides changed it but ended up with the same final term list &rarr; converged. Not a conflict.</li>
 *   <li>Both sides changed it, and they agree on the preferred term (PT, i.e. the first term in the list), but
 *       differ only in synonyms &rarr; auto-merge as a union of both sides' terms (PT first). Not a conflict -
 *       this is the "non-overlapping field/term change" case.</li>
 *   <li>Both sides changed it, and they disagree on the PT &rarr; genuine overlapping conflict. Snowstorm's
 *       (upstream) term list wins entirely; the local edit is discarded. Recorded so the caller can notify the
 *       user which concepts were overwritten (the actual UI mechanism is not yet designed).</li>
 * </ul>
 * <p>
 * Note: SNOMED description metadata such as case significance is not part of {@link TranslationState} (it only
 * holds term strings), so a case-significance-only edit is just treated as an ordinary term content change here.
 */
public final class TranslationRebaseMergeResolver {

	private TranslationRebaseMergeResolver() {
	}

	/** Which concepts had their local Translation Studio/Snolate change overwritten by an upstream conflict. */
	public record RebaseMergeResult(TranslationState mergedState, List<Long> overwrittenConceptIds) {
	}

	public static RebaseMergeResult resolve(TranslationState baseline, TranslationState local, TranslationState upstream) {
		TranslationState merged = new TranslationState();
		List<Long> overwritten = new ArrayList<>();

		Set<Long> allConceptIds = new LinkedHashSet<>();
		allConceptIds.addAll(baseline.getConceptTerms().keySet());
		allConceptIds.addAll(local.getConceptTerms().keySet());
		allConceptIds.addAll(upstream.getConceptTerms().keySet());

		for (Long conceptId : allConceptIds) {
			List<String> baselineTerms = TranslationStateDiff.termsForConcept(baseline, conceptId);
			List<String> localTerms = TranslationStateDiff.termsForConcept(local, conceptId);
			List<String> upstreamTerms = TranslationStateDiff.termsForConcept(upstream, conceptId);

			ConceptMergeOutcome outcome = resolveConcept(baselineTerms, localTerms, upstreamTerms);
			if (!outcome.finalTerms().isEmpty()) {
				merged.getConceptTerms().put(conceptId, outcome.finalTerms());
			}
			if (outcome.conflict()) {
				overwritten.add(conceptId);
			}
		}

		return new RebaseMergeResult(merged, overwritten);
	}

	private record ConceptMergeOutcome(List<String> finalTerms, boolean conflict) {
	}

	private static ConceptMergeOutcome resolveConcept(List<String> baselineTerms, List<String> localTerms, List<String> upstreamTerms) {
		boolean localChanged = !localTerms.equals(baselineTerms);
		boolean upstreamChanged = !upstreamTerms.equals(baselineTerms);

		if (!localChanged && !upstreamChanged) {
			// Neither side touched this concept.
			return new ConceptMergeOutcome(baselineTerms, false);
		}
		if (!localChanged) {
			// Only upstream changed it - trivial, no conflict (covers: term added/removed upstream only).
			return new ConceptMergeOutcome(upstreamTerms, false);
		}
		if (!upstreamChanged) {
			// Only local changed it - trivial, no conflict (covers: term added/removed locally only).
			return new ConceptMergeOutcome(localTerms, false);
		}

		// Both sides changed this concept.
		if (localTerms.equals(upstreamTerms)) {
			// Converged on the same result independently - not a real conflict.
			return new ConceptMergeOutcome(upstreamTerms, false);
		}

		String localPt = firstOrNull(localTerms);
		String upstreamPt = firstOrNull(upstreamTerms);
		if (Objects.equals(localPt, upstreamPt)) {
			// PT agrees; only synonyms differ - non-overlapping, auto-merge as a union.
			return new ConceptMergeOutcome(unionPreservingPtFirst(localPt, localTerms, upstreamTerms), false);
		}

		// PT disagreement: a genuine overlapping conflict. Upstream (Snowstorm) wins entirely; local is discarded.
		return new ConceptMergeOutcome(upstreamTerms, true);
	}

	private static String firstOrNull(List<String> terms) {
		return terms.isEmpty() ? null : terms.get(0);
	}

	private static List<String> unionPreservingPtFirst(String pt, List<String> localTerms, List<String> upstreamTerms) {
		Set<String> union = new LinkedHashSet<>();
		if (pt != null) {
			union.add(pt);
		}
		union.addAll(localTerms);
		union.addAll(upstreamTerms);
		return new ArrayList<>(union);
	}
}
