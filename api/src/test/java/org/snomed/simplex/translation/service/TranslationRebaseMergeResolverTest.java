package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationRebaseMergeResolver.RebaseMergeResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enumerates the merge scenarios for a REBASE vs. unsynced local edit,
 * concept by concept. Each test is independent so the scenario matrix is reviewable test-by-test.
 */
class TranslationRebaseMergeResolverTest {

	private static final Long CONCEPT = 100L;

	@Test
	void neitherSideChanged_noOp() {
		RebaseMergeResult result = resolve(List.of("stable"), List.of("stable"), List.of("stable"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("stable");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void onlyLocalChanged_localWins_noConflict() {
		// Local edited a term; upstream still matches baseline (didn't touch this concept).
		RebaseMergeResult result = resolve(List.of("old"), List.of("new"), List.of("old"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("new");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void onlyLocalChanged_addedNewConcept_localWins_noConflict() {
		// Concept didn't exist in baseline or upstream; it's new in Translation Studio only.
		RebaseMergeResult result = resolve(List.of(), List.of("brand new"), List.of());

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("brand new");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void onlyUpstreamChanged_termAdded_upstreamWins_noConflict() {
		// Term added upstream (e.g. a new synonym from another project); local never touched this concept.
		RebaseMergeResult result = resolve(List.of("preferred"), List.of("preferred"), List.of("preferred", "new synonym"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("preferred", "new synonym");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void onlyUpstreamChanged_termRemoved_upstreamWins_noConflict() {
		// Term removed upstream; local still shows the baseline content (never edited this concept).
		RebaseMergeResult result = resolve(List.of("preferred", "old synonym"), List.of("preferred", "old synonym"), List.of("preferred"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("preferred");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void bothChangedIdentically_converged_noConflict() {
		// Local and upstream independently arrived at the exact same new term list.
		RebaseMergeResult result = resolve(List.of("old"), List.of("new"), List.of("new"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("new");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void bothChangedDifferentSynonymsSamePt_autoMergedAsUnion_noConflict() {
		// Multi-term concept: PT agrees, but each side added a *different* synonym - non-overlapping, union merge.
		RebaseMergeResult result = resolve(
				List.of("preferred"),
				List.of("preferred", "local synonym"),
				List.of("preferred", "upstream synonym"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT))
				.containsExactly("preferred", "local synonym", "upstream synonym");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void bothChangedDifferentPt_conflict_upstreamWinsEntirely() {
		// Classic rename conflict: both sides renamed the PT to something different. Overlapping -> upstream wins.
		RebaseMergeResult result = resolve(
				List.of("Heart attack"),
				List.of("Myocardial infarction"),
				List.of("Cardiac infarction"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("Cardiac infarction");
		assertThat(result.overwrittenConceptIds()).containsExactly(CONCEPT);
	}

	@Test
	void localRemovedAllTerms_upstreamKeptPt_conflict_upstreamWins() {
		// Local deleted the translation entirely (empty PT); upstream still has a PT - disagreement over presence.
		RebaseMergeResult result = resolve(List.of("preferred"), List.of(), List.of("preferred", "synonym"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("preferred", "synonym");
		assertThat(result.overwrittenConceptIds()).containsExactly(CONCEPT);
	}

	@Test
	void upstreamRemovedAllTerms_localKeptPt_conflict_upstreamWinsAndConceptDropped() {
		// Upstream deleted the translation entirely; local still has content - upstream wins, concept drops out.
		RebaseMergeResult result = resolve(List.of("preferred"), List.of("preferred", "local synonym"), List.of());

		assertThat(result.mergedState().getConceptTerms()).doesNotContainKey(CONCEPT);
		assertThat(result.overwrittenConceptIds()).containsExactly(CONCEPT);
	}

	@Test
	void caseSignificanceOnlyDifference_treatedAsOrdinaryTermEdit() {
		// TranslationState has no case-significance field - a case-only edit is just a different term string.
		// Both sides change the PT's casing differently -> ordinary PT conflict, upstream wins.
		RebaseMergeResult result = resolve(List.of("heart attack"), List.of("Heart attack"), List.of("Heart Attack"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT)).containsExactly("Heart Attack");
		assertThat(result.overwrittenConceptIds()).containsExactly(CONCEPT);
	}

	@Test
	void bothRemoveSameTerm_keepDifferentSynonyms_noPtConflict_unionMerge() {
		// Multi-term: both sides drop an old synonym (agreement) but each keeps/adds a different remaining one.
		RebaseMergeResult result = resolve(
				List.of("preferred", "old synonym"),
				List.of("preferred", "local synonym"),
				List.of("preferred", "upstream synonym"));

		assertThat(result.mergedState().getConceptTerms().get(CONCEPT))
				.containsExactly("preferred", "local synonym", "upstream synonym");
		assertThat(result.overwrittenConceptIds()).isEmpty();
	}

	@Test
	void multipleConceptsIndependentOutcomes() {
		TranslationState baseline = new TranslationState();
		baseline.getConceptTerms().put(1L, List.of("a"));
		baseline.getConceptTerms().put(2L, List.of("b"));
		baseline.getConceptTerms().put(3L, List.of("c"));

		TranslationState local = new TranslationState();
		local.getConceptTerms().put(1L, List.of("a")); // untouched
		local.getConceptTerms().put(2L, List.of("b2")); // local-only edit
		local.getConceptTerms().put(3L, List.of("c-local")); // conflicting rename

		TranslationState upstream = new TranslationState();
		upstream.getConceptTerms().put(1L, List.of("a-upstream")); // upstream-only edit
		upstream.getConceptTerms().put(2L, List.of("b")); // untouched
		upstream.getConceptTerms().put(3L, List.of("c-upstream")); // conflicting rename

		RebaseMergeResult result = TranslationRebaseMergeResolver.resolve(baseline, local, upstream);

		assertThat(result.mergedState().getConceptTerms().get(1L)).containsExactly("a-upstream");
		assertThat(result.mergedState().getConceptTerms().get(2L)).containsExactly("b2");
		assertThat(result.mergedState().getConceptTerms().get(3L)).containsExactly("c-upstream");
		assertThat(result.overwrittenConceptIds()).containsExactly(3L);
	}

	private static RebaseMergeResult resolve(List<String> baselineTerms, List<String> localTerms, List<String> upstreamTerms) {
		TranslationState baseline = singleConceptState(baselineTerms);
		TranslationState local = singleConceptState(localTerms);
		TranslationState upstream = singleConceptState(upstreamTerms);
		return TranslationRebaseMergeResolver.resolve(baseline, local, upstream);
	}

	private static TranslationState singleConceptState(List<String> terms) {
		TranslationState state = new TranslationState();
		if (!terms.isEmpty()) {
			state.getConceptTerms().put(CONCEPT, terms);
		}
		return state;
	}
}
