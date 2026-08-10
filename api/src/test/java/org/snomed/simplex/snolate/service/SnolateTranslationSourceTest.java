package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationSourceTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateTranslationUnitStore translationUnitStore;
	@Mock
	private SnolateTranslationSearchService translationSearchService;

	private SnolateTranslationSource source;

	@BeforeEach
	void setUp() {
		source = new SnolateTranslationSource(translationUnitStore, translationSearchService, LANG, REFSET);
	}

	@Test
	void readTranslation_mapsPersistedUnits() throws Exception {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(new TranslationUnit("100", COMPOSITE, List.of("alpha", "beta"), TranslationStatus.FOR_REVIEW));
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsEntry(100L, List.of("alpha", "beta"));
	}

	@Test
	void readTranslation_rejectsNonNumericCode() {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(new TranslationUnit("x", COMPOSITE, List.of("t"), TranslationStatus.APPROVED));
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		assertThatThrownBy(source::readTranslation)
				.hasMessageContaining("non-numeric code");
	}

	@Test
	void applyDelta_addsAndRemovesTerms() throws Exception {
		TranslationUnit unit = new TranslationUnit(
				new TranslationUnit.MembershipKey("200", REFSET, LANG, COMPOSITE, 0),
				new ArrayList<>(List.of("existing", "old")),
				TranslationStatus.APPROVED,
				Set.of());
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("200", unit));

		TranslationIntent delta = new TranslationIntent();
		delta.getTermIntents().put(200L, List.of(
				new TermIntent("existing", Intent.NONE),
				new TermIntent("extra", Intent.ADD),
				new TermIntent("old", Intent.REMOVE)));

		source.applyDelta(delta);

		assertThat(unit.getTerms()).containsExactly("existing", "extra");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore, times(1)).saveAll(captor.capture());
		assertThat(captor.getValue()).contains(unit);
	}

	@Test
	void applyDelta_noUnitForConcept_isNoOp() throws Exception {
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any())).thenReturn(Map.of());

		TranslationIntent delta = new TranslationIntent();
		delta.getTermIntents().put(999L, List.of(new TermIntent("term", Intent.ADD)));

		source.applyDelta(delta);

		verify(translationUnitStore, times(0)).saveAll(any());
	}

	@Test
	void applyDelta_snowstormRemoveOutsideSet() throws Exception {
		TranslationUnit unit = new TranslationUnit(
				new TranslationUnit.MembershipKey("300", REFSET, LANG, COMPOSITE, 0),
				new ArrayList<>(List.of("removed term")),
				TranslationStatus.COMPLETE,
				new LinkedHashSet<>(Set.of("other-set")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("300"))).thenReturn(Map.of("300", unit));

		TranslationIntent delta = new TranslationIntent();
		delta.getTermIntents().put(300L, List.of(new TermIntent("removed term", Intent.REMOVE)));

		source.applyDelta(delta);

		assertThat(unit.getTerms()).isEmpty();
		verify(translationUnitStore).saveAll(any());
	}

	@Test
	void readTranslation_ignoresOtherLanguageBuckets() throws Exception {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(new TranslationUnit("100", COMPOSITE, List.of("en-term"), TranslationStatus.APPROVED));
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsOnlyKeys(100L);
	}

	@Test
	void mergeAdditions_prependsWhenUnitWasEmpty() {
		assertThat(SnolateTranslationSource.mergeAdditions(List.of(), List.of("b", "a")))
				.containsExactly("b", "a");
	}

}
