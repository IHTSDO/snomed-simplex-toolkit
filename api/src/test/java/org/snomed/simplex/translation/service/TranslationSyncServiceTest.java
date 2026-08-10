package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationSyncServiceTest {

	private TranslationSyncService syncService;

	@BeforeEach
	void setUp() {
		syncService = new TranslationSyncService();
	}

	@Test
	void planPushUpload_snolateAdd_includesNewTerm() {
		TranslationState previous = state(100L, List.of("preferred"));
		TranslationState current = state(100L, List.of("preferred"));
		TranslationState snolate = state(100L, List.of("preferred", "new synonym"));

		TranslationSyncService.PushPlan plan = syncService.planPushUpload(
				previous, current, TranslationStateDiff.diff(previous, current), snolate, Set.of(100L));

		assertThat(plan.hasSnowstormUpload()).isTrue();
		assertThat(plan.snowstormUpload().getConceptTerms()).containsEntry(100L, List.of("preferred", "new synonym"));
		assertThat(plan.postPushSnapshot().getConceptTerms()).containsEntry(100L, List.of("preferred", "new synonym"));
	}

	@Test
	void planPushUpload_snolateRemove_omitsStableSnowstormTerm() {
		TranslationState previous = state(100L, List.of("preferred", "old synonym"));
		TranslationState current = state(100L, List.of("preferred", "old synonym"));
		TranslationState snolate = state(100L, List.of("preferred"));

		TranslationSyncService.PushPlan plan = syncService.planPushUpload(
				previous, current, TranslationStateDiff.diff(previous, current), snolate, Set.of(100L));

		assertThat(plan.hasSnowstormUpload()).isTrue();
		assertThat(plan.snowstormUpload().getConceptTerms()).containsEntry(100L, List.of("preferred"));
	}

	@Test
	void planPushUpload_snowstormAheadAdd_preservedWhenSnolateAlsoChanges() {
		TranslationState previous = state(100L, List.of("preferred"));
		TranslationState current = state(100L, List.of("preferred", "external synonym"));
		TranslationState snolate = state(100L, List.of("preferred", "new synonym"));
		TranslationIntent delta = TranslationStateDiff.diff(previous, current);

		TranslationSyncService.PushPlan plan = syncService.planPushUpload(
				previous, current, delta, snolate, Set.of(100L));

		assertThat(plan.hasSnowstormUpload()).isTrue();
		assertThat(plan.snowstormUpload().getConceptTerms().get(100L))
				.containsExactly("preferred", "new synonym", "external synonym");
	}

	@Test
	void planPushUpload_unchangedConcept_noUpload() {
		TranslationState previous = state(100L, List.of("preferred", "synonym"));
		TranslationState current = state(100L, List.of("preferred", "synonym"));
		TranslationState snolate = state(100L, List.of("preferred", "synonym"));

		TranslationSyncService.PushPlan plan = syncService.planPushUpload(
				previous, current, TranslationStateDiff.diff(previous, current), snolate, Set.of(100L));

		assertThat(plan.hasSnowstormUpload()).isFalse();
		assertThat(plan.snowstormUpload().getConceptTerms()).isEmpty();
		assertThat(plan.postPushSnapshot().getConceptTerms()).containsEntry(100L, List.of("preferred", "synonym"));
	}

	@Test
	void planPushUpload_blankPrevious_termInBoth_noUpload() {
		TranslationState previous = new TranslationState();
		TranslationState current = state(233940007L, List.of("pulmonale tumorembolie"));
		TranslationState snolate = state(233940007L, List.of("pulmonale tumorembolie"));

		TranslationSyncService.PushPlan plan = syncService.planPushUpload(
				previous, current, TranslationStateDiff.diff(previous, current), snolate, Set.of(233940007L));

		assertThat(plan.hasSnowstormUpload()).isFalse();
	}

	@Test
	void planPushUpload_blankPrevious_newSnolateTerm_uploadsAdd() {
		TranslationState previous = new TranslationState();
		TranslationState current = state(200L, List.of());
		TranslationState snolate = state(200L, List.of("new pt"));

		TranslationSyncService.PushPlan plan = syncService.planPushUpload(
				previous, current, TranslationStateDiff.diff(previous, current), snolate, Set.of(200L));

		assertThat(plan.hasSnowstormUpload()).isTrue();
		assertThat(plan.snowstormUpload().getConceptTerms()).containsEntry(200L, List.of("new pt"));
	}

	@Test
	void buildTargetTerms_snolateOrderWinsPlusSnowstormAhead() {
		TranslationIntent delta = new TranslationIntent();
		delta.getTermIntents().put(100L, List.of(new TermIntent("external", Intent.ADD)));

		List<String> target = TranslationSyncService.buildTargetTerms(
				List.of("preferred", "synonym"),
				List.of("preferred", "synonym", "external"),
				delta,
				100L);

		assertThat(target).containsExactly("preferred", "synonym", "external");
	}

	private static TranslationState state(long conceptId, List<String> terms) {
		TranslationState state = new TranslationState();
		state.getConceptTerms().put(conceptId, terms);
		return state;
	}

}
