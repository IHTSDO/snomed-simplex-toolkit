package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.authoringservices.AuthoringServicesClient;
import org.snomed.simplex.service.SimpleRefsetService;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceCompleteStatusTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SimpleRefsetService refsetService;
	@Mock
	private SnowstormClientFactory snowstormClientFactory;
	@Mock
	private AuthoringServicesClient authoringServicesClient;
	@Mock
	private TranslationMergeService translationMergeService;
	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;

	private TranslationService translationService;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		translationService = new TranslationService(refsetService, snowstormClientFactory, authoringServicesClient,
				translationMergeService, translationUnitRepository, translationSearchService);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void markPulledUnitsComplete_setsCompleteForUnitsWithTermsOnly() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit translated = unit("100", List.of("term"), TranslationStatus.APPROVED, setCode);
		TranslationUnit shell = unit("200", List.of(), TranslationStatus.NOT_STARTED, setCode);

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			consumer.accept(translated);
			consumer.accept(shell);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(setCode), eq(COMPOSITE), any());

		invokeMarkPulledUnitsComplete(translationSet);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<TranslationUnit>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>();
		captor.getValue().forEach(saved::add);
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getCode()).isEqualTo("100");
		assertThat(saved.get(0).getStatus()).isEqualTo(TranslationStatus.COMPLETE);
		assertThat(shell.getStatus()).isEqualTo(TranslationStatus.NOT_STARTED);
	}

	@Test
	void markSnowstormMatchingUnitsComplete_marksMatchingNonNeedsEditUnits() throws Exception {
		TranslationState snowstormState = new TranslationState();
		snowstormState.getConceptTerms().put(100L, List.of("preferred", "syn"));
		snowstormState.getConceptTerms().put(200L, List.of("match"));
		snowstormState.getConceptTerms().put(300L, List.of("snow"));

		TranslationUnit forReviewMatch = unit("100", List.of("preferred", "syn"), TranslationStatus.FOR_REVIEW, "set");
		TranslationUnit approvedMatch = unit("200", List.of("match"), TranslationStatus.APPROVED, "set");
		TranslationUnit needsEditMatch = unit("300", List.of("snow"), TranslationStatus.NEEDS_EDIT, "set");
		TranslationUnit mismatch = unit("400", List.of("different"), TranslationStatus.FOR_REVIEW, "set");
		TranslationUnit shell = unit("500", List.of(), TranslationStatus.NOT_STARTED, "set");

		when(translationUnitRepository.findAllByCompositeLanguageCode(COMPOSITE))
				.thenReturn(List.of(forReviewMatch, approvedMatch, needsEditMatch, mismatch, shell));

		invokeMarkSnowstormMatchingUnitsComplete(COMPOSITE, snowstormState);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<TranslationUnit>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>();
		captor.getValue().forEach(saved::add);
		assertThat(saved).extracting(TranslationUnit::getCode).containsExactlyInAnyOrder("100", "200");
		assertThat(saved).allMatch(u -> u.getStatus() == TranslationStatus.COMPLETE);
		assertThat(needsEditMatch.getStatus()).isEqualTo(TranslationStatus.NEEDS_EDIT);
		assertThat(mismatch.getStatus()).isEqualTo(TranslationStatus.FOR_REVIEW);
	}

	private TranslationUnit unit(String code, List<String> terms, TranslationStatus status, String setCode) {
		return new TranslationUnit(code, REFSET, LANG, COMPOSITE, 0, terms, status, new LinkedHashSet<>(Set.of(setCode)));
	}

	private void invokeMarkPulledUnitsComplete(SnolateTranslationSet set) throws Exception {
		Method method = TranslationService.class.getDeclaredMethod("markPulledUnitsComplete", SnolateTranslationSet.class);
		method.setAccessible(true);
		method.invoke(translationService, set);
	}

	private void invokeMarkSnowstormMatchingUnitsComplete(String compositeLanguageCode, TranslationState snowstormState)
			throws Exception {
		Method method = TranslationService.class.getDeclaredMethod("markSnowstormMatchingUnitsComplete", String.class,
				TranslationState.class);
		method.setAccessible(true);
		method.invoke(translationService, compositeLanguageCode, snowstormState);
	}
}
