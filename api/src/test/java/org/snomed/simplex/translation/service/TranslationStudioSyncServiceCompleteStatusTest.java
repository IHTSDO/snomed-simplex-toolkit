package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.repository.TranslationStateRepository;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationStudioSyncServiceCompleteStatusTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private TranslationService translationService;
	@Mock
	private TranslationSyncService translationSyncService;
	@Mock
	private TranslationStateRepository translationStateRepository;
	@Mock
	private SnolateTranslationUnitStore translationUnitStore;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;

	private TranslationStudioSyncService syncService;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		syncService = new TranslationStudioSyncService(translationService, translationSyncService, translationStateRepository,
				translationUnitStore, translationSearchService, translationSourceRepository);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void markPulledUnitsComplete_setsCompleteForUnitsWithTermsOnly() {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit translated = unit("100", List.of("term"), TranslationStatus.APPROVED, setCode);
		TranslationUnit shell = unit("200", List.of(), TranslationStatus.NOT_STARTED, setCode);

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			consumer.accept(translated);
			consumer.accept(shell);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(setCode), eq(COMPOSITE), any());

		syncService.markPulledUnitsComplete(translationSet, true);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>(captor.getValue());
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getCode()).isEqualTo("100");
		assertThat(saved.get(0).getStatus()).isEqualTo(TranslationStatus.COMPLETE);
		assertThat(shell.getStatus()).isEqualTo(TranslationStatus.NOT_STARTED);
	}

	@Test
	void markPulledUnitsComplete_excludesForReviewWhenNotIncluded() {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit approved = unit("100", List.of("approved"), TranslationStatus.APPROVED, setCode);
		TranslationUnit forReview = unit("200", List.of("for review"), TranslationStatus.FOR_REVIEW, setCode);

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			consumer.accept(approved);
			consumer.accept(forReview);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(setCode), eq(COMPOSITE), any());

		syncService.markPulledUnitsComplete(translationSet, false);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>(captor.getValue());
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getCode()).isEqualTo("100");
		assertThat(saved.get(0).getStatus()).isEqualTo(TranslationStatus.COMPLETE);
		assertThat(forReview.getStatus()).isEqualTo(TranslationStatus.FOR_REVIEW);
	}

	@Test
	void markSnowstormMatchingUnitsComplete_marksMatchingNonNeedsEditUnits() {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of("preferred", "syn"));
		snowstormState.getConceptTerms().put(200L, List.of("match"));
		snowstormState.getConceptTerms().put(300L, List.of("snow"));

		TranslationUnit forReviewMatch = unit("100", List.of("preferred", "syn"), TranslationStatus.FOR_REVIEW, "set");
		TranslationUnit approvedMatch = unit("200", List.of("match"), TranslationStatus.APPROVED, "set");
		TranslationUnit needsEditMatch = unit("300", List.of("snow"), TranslationStatus.NEEDS_EDIT, "set");
		TranslationUnit mismatch = unit("400", List.of("different"), TranslationStatus.FOR_REVIEW, "set");
		TranslationUnit shell = unit("500", List.of(), TranslationStatus.NOT_STARTED, "set");

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(forReviewMatch);
			consumer.accept(approvedMatch);
			consumer.accept(needsEditMatch);
			consumer.accept(mismatch);
			consumer.accept(shell);
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		syncService.markSnowstormMatchingUnitsComplete(COMPOSITE, snowstormState);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>(captor.getValue());
		assertThat(saved).extracting(TranslationUnit::getCode).containsExactlyInAnyOrder("100", "200");
		assertThat(saved).allMatch(u -> u.getStatus() == TranslationStatus.COMPLETE);
		assertThat(needsEditMatch.getStatus()).isEqualTo(TranslationStatus.NEEDS_EDIT);
		assertThat(mismatch.getStatus()).isEqualTo(TranslationStatus.FOR_REVIEW);
	}

	@Test
	void markSnowstormMatchingUnitsComplete_marksCompleteWhenSynonymOrderDiffers() {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of("preferred", "synA", "synB"));

		TranslationUnit reorderedSynonyms = unit("100", List.of("preferred", "synB", "synA"), TranslationStatus.APPROVED, "set");

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(reorderedSynonyms);
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		syncService.markSnowstormMatchingUnitsComplete(COMPOSITE, snowstormState);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>(captor.getValue());
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getCode()).isEqualTo("100");
		assertThat(saved.get(0).getStatus()).isEqualTo(TranslationStatus.COMPLETE);
	}

	@Test
	void createMissingUnitsFromSnowstorm_createsUnitWithSnowstormTermsAndCompleteStatus() {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of("preferred", "syn"));

		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("100"))).thenReturn(Map.of());
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 42)));

		syncService.createMissingUnitsFromSnowstorm(LANG, REFSET, snowstormState);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>(captor.getValue());
		assertThat(saved).hasSize(1);
		TranslationUnit unit = saved.get(0);
		assertThat(unit.getCode()).isEqualTo("100");
		assertThat(unit.getTerms()).containsExactly("preferred", "syn");
		assertThat(unit.getStatus()).isEqualTo(TranslationStatus.COMPLETE);
		assertThat(unit.getMemberOf()).isEmpty();
		assertThat(unit.getOrder()).isEqualTo(42);
		assertThat(unit.getCompositeLanguageCode()).isEqualTo(COMPOSITE);
	}

	@Test
	void createMissingUnitsFromSnowstorm_skipsWhenUnitAlreadyExists() {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of("term"));

		TranslationUnit existing = unit("100", List.of("existing"), TranslationStatus.APPROVED, "set");
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("100"))).thenReturn(Map.of("100", existing));

		syncService.createMissingUnitsFromSnowstorm(LANG, REFSET, snowstormState);

		verify(translationUnitStore, never()).saveAll(any());
	}

	@Test
	void createMissingUnitsFromSnowstorm_skipsWhenNoTranslationSource() {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of("term"));

		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("100"))).thenReturn(Map.of());
		when(translationSourceRepository.findAllById(List.of("100"))).thenReturn(List.of());

		syncService.createMissingUnitsFromSnowstorm(LANG, REFSET, snowstormState);

		verify(translationUnitStore, never()).saveAll(any());
	}

	@Test
	void createMissingUnitsFromSnowstorm_skipsWhenSnowstormTermsEmpty() {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of());
		snowstormState.getConceptTerms().put(200L, List.of("  ", ""));

		syncService.createMissingUnitsFromSnowstorm(LANG, REFSET, snowstormState);

		verify(translationUnitStore, never()).saveAll(any());
		verify(translationSourceRepository, never()).findAllById(any());
	}

	private TranslationUnit unit(String code, List<String> terms, TranslationStatus status, String setCode) {
		return new TranslationUnit(new TranslationUnit.MembershipKey(code, REFSET, LANG, COMPOSITE, 0), terms, status,
				new LinkedHashSet<>(Set.of(setCode)));
	}
}
