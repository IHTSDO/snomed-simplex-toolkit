package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationServiceTermMatchTest {

	@Test
	void orderedTermsMatch_requiresSameOrderIncludingPreferredTerm() {
		assertThat(TranslationStudioSyncService.orderedTermsMatch(
				List.of("preferred", "synonym"),
				List.of("preferred", "synonym"))).isTrue();
		assertThat(TranslationStudioSyncService.orderedTermsMatch(
				List.of("synonym", "preferred"),
				List.of("preferred", "synonym"))).isFalse();
	}

	@Test
	void orderedTermsMatch_trimsAndIgnoresBlankEntries() {
		assertThat(TranslationStudioSyncService.orderedTermsMatch(
				List.of("  preferred  ", ""),
				List.of("preferred"))).isTrue();
	}

	@Test
	void orderedTermsMatch_emptyListsMatch() {
		assertThat(TranslationStudioSyncService.orderedTermsMatch(List.of(), List.of())).isTrue();
		assertThat(TranslationStudioSyncService.orderedTermsMatch(List.of("a"), List.of())).isFalse();
	}
}
