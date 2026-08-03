package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.translation.domain.TranslationState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateSubsetTranslationSourceTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String SET_CODE = "test-set";

	@Mock
	private SnolateTranslationSearchService translationSearchService;

	@Test
	void readTranslation_includesAllUnitsWithTermsWhenReadyForReviewIncluded() throws Exception {
		SnolateSubsetTranslationSource source = new SnolateSubsetTranslationSource(
				translationSearchService, LANG, REFSET, SET_CODE, true);
		when(translationSearchService.listAllUnitsInSet(SET_CODE, LANG + "-" + REFSET))
				.thenReturn(List.of(
						unit("100", List.of("approved"), TranslationStatus.APPROVED),
						unit("200", List.of("for review"), TranslationStatus.FOR_REVIEW)));

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsOnlyKeys(100L, 200L);
	}

	@Test
	void readTranslation_excludesForReviewUnitsWhenNotIncluded() throws Exception {
		SnolateSubsetTranslationSource source = new SnolateSubsetTranslationSource(
				translationSearchService, LANG, REFSET, SET_CODE, false);
		when(translationSearchService.listAllUnitsInSet(SET_CODE, LANG + "-" + REFSET))
				.thenReturn(List.of(
						unit("100", List.of("approved"), TranslationStatus.APPROVED),
						unit("200", List.of("for review"), TranslationStatus.FOR_REVIEW)));

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsOnlyKeys(100L);
		assertThat(state.getConceptTerms().get(100L)).containsExactly("approved");
	}

	private TranslationUnit unit(String code, List<String> terms, TranslationStatus status) {
		return new TranslationUnit(
				new TranslationUnit.MembershipKey(code, REFSET, LANG, LANG + "-" + REFSET, 0),
				terms,
				status,
				new LinkedHashSet<>(Set.of(SET_CODE)));
	}
}
