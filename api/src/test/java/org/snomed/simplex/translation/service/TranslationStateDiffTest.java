package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationStateDiffTest {

	@Test
	void diff_detectsAddAndRemove() {
		TranslationState previous = new TranslationState();
		previous.getConceptTerms().put(100L, List.of("old", "stable"));

		TranslationState current = new TranslationState();
		current.getConceptTerms().put(100L, List.of("stable", "new"));

		TranslationIntent delta = TranslationStateDiff.diff(previous, current);

		assertThat(delta.getTermIntents().get(100L)).containsExactly(
				new TermIntent("stable", Intent.NONE),
				new TermIntent("new", Intent.ADD),
				new TermIntent("old", Intent.REMOVE));
	}

	@Test
	void applyIntent_addAndRemove() {
		List<String> result = TranslationStateDiff.applyIntent(
				List.of("preferred", "old"),
				List.of(new TermIntent("synonym", Intent.ADD), new TermIntent("old", Intent.REMOVE)));

		assertThat(result).containsExactly("preferred", "synonym");
	}

	@Test
	void applyIntent_addExistingTerm_doesNotDuplicate() {
		List<String> result = TranslationStateDiff.applyIntent(
				List.of("preferred"),
				List.of(new TermIntent("preferred", Intent.ADD)));

		assertThat(result).containsExactly("preferred");
	}

	@Test
	void hasAdditionsOrRemovals() {
		TranslationIntent noneOnly = new TranslationIntent();
		noneOnly.getTermIntents().put(100L, List.of(new TermIntent("term", Intent.NONE)));

		TranslationIntent withAdd = new TranslationIntent();
		withAdd.getTermIntents().put(100L, List.of(new TermIntent("term", Intent.ADD)));

		assertThat(TranslationStateDiff.hasAdditionsOrRemovals(noneOnly)).isFalse();
		assertThat(TranslationStateDiff.hasAdditionsOrRemovals(withAdd)).isTrue();
	}

}
