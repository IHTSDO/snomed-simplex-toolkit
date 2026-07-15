package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.rest.pojos.BatchTranslateRequest;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.BatchTranslationPrompt;
import org.snomed.simplex.translation.TranslationLLMService;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.jms.core.JmsTemplate;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateBatchTranslationServiceTest {

	private static final String LANG = "es";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateSetRepository snolateSetRepository;
	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private TranslationLLMService translationLLMService;

	private SnolateBatchTranslationService service;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		SnolateProcessingContext ctx = new SnolateProcessingContext(
				org.mockito.Mockito.mock(SnowstormClientFactory.class),
				snolateSetRepository,
				translationSourceRepository,
				translationUnitRepository,
				translationSearchService,
				translationLLMService,
				new HashMap<>(),
				org.mockito.Mockito.mock(JmsTemplate.class),
				"test-queue",
				new ObjectMapper());
		service = new SnolateBatchTranslationService(ctx);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void doRunAiBatchTranslate_storesSuggestionsNotTerms() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit emptyUnit = shellUnit("100", 0, setCode);
		when(translationSearchService.listAllUnitsInSet(setCode, COMPOSITE)).thenReturn(List.of(emptyUnit));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Asthma", List.of("Asma")));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("100", COMPOSITE))
				.thenReturn(Optional.of(emptyUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		ArgumentCaptor<TranslationUnit> captor = ArgumentCaptor.forClass(TranslationUnit.class);
		verify(translationUnitRepository).save(captor.capture());
		TranslationUnit saved = captor.getValue();
		assertThat(saved.getTerms()).isEmpty();
		assertThat(saved.getAiSuggestions()).containsExactly("Asma");
		assertThat(saved.getStatus()).isEqualTo(TranslationStatus.NOT_STARTED);
	}

	@Test
	void doRunAiBatchTranslate_skipsUnitsWithExistingSuggestions() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit suggestedUnit = shellUnit("100", 0, setCode);
		suggestedUnit.setAiSuggestions(List.of("Asma existente"));
		when(translationSearchService.listAllUnitsInSet(setCode, COMPOSITE)).thenReturn(List.of(suggestedUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		verify(translationLLMService, never()).suggestBatchTranslations(any(), any());
		verify(translationUnitRepository, never()).save(any());
	}

	@Test
	void doRunAiBatchTranslate_includesAcceptedContextInPrompt() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit contextA = unit("100", 0, setCode, TranslationStatus.APPROVED, List.of("Asma"));
		TranslationUnit contextB = unit("200", 1, setCode, TranslationStatus.COMPLETE, List.of("Diabetes"));
		TranslationUnit emptyUnit = shellUnit("300", 2, setCode);
		List<TranslationUnit> ordered = List.of(contextA, contextB, emptyUnit);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"200", new TranslationSource("200", "Diabetes mellitus", 1),
				"300", new TranslationSource("300", "Heart failure", 2));

		when(translationSearchService.listAllUnitsInSet(setCode, COMPOSITE)).thenReturn(ordered);
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			return codes.stream().map(sources::get).toList();
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Heart failure", List.of("Insuficiencia cardíaca")));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("300", COMPOSITE))
				.thenReturn(Optional.of(emptyUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		ArgumentCaptor<BatchTranslationPrompt> promptCaptor = ArgumentCaptor.forClass(BatchTranslationPrompt.class);
		verify(translationLLMService).suggestBatchTranslations(eq(translationSet), promptCaptor.capture());
		BatchTranslationPrompt prompt = promptCaptor.getValue();
		assertThat(prompt.promptLines()).containsExactly(
				"1|Asthma → Asma",
				"2|Diabetes mellitus → Diabetes",
				"3|Heart failure");
	}

	@Test
	void doRunAiBatchTranslate_walksBackWhenImmediatePredecessorNotAccepted() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit contextA = unit("100", 0, setCode, TranslationStatus.APPROVED, List.of("Asma"));
		TranslationUnit notStarted = shellUnit("200", 1, setCode);
		notStarted.setAiSuggestions(List.of("pending"));
		TranslationUnit emptyUnit = shellUnit("300", 2, setCode);
		List<TranslationUnit> ordered = List.of(contextA, notStarted, emptyUnit);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"200", new TranslationSource("200", "Bronchitis", 1),
				"300", new TranslationSource("300", "Heart failure", 2));

		when(translationSearchService.listAllUnitsInSet(setCode, COMPOSITE)).thenReturn(ordered);
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			return codes.stream().map(sources::get).toList();
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Heart failure", List.of("Insuficiencia cardíaca")));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("300", COMPOSITE))
				.thenReturn(Optional.of(emptyUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		ArgumentCaptor<BatchTranslationPrompt> promptCaptor = ArgumentCaptor.forClass(BatchTranslationPrompt.class);
		verify(translationLLMService).suggestBatchTranslations(eq(translationSet), promptCaptor.capture());
		assertThat(promptCaptor.getValue().promptLines()).containsExactly(
				"1|Asthma → Asma",
				"2|Heart failure");
	}

	@Test
	void doRunAiBatchTranslate_excludesForReviewFromContext() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit forReview = unit("100", 0, setCode, TranslationStatus.FOR_REVIEW, List.of("Asma provisional"));
		TranslationUnit emptyUnit = shellUnit("200", 1, setCode);
		List<TranslationUnit> ordered = List.of(forReview, emptyUnit);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"200", new TranslationSource("200", "Heart failure", 1));

		when(translationSearchService.listAllUnitsInSet(setCode, COMPOSITE)).thenReturn(ordered);
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			return codes.stream().map(sources::get).toList();
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Heart failure", List.of("Insuficiencia cardíaca")));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("200", COMPOSITE))
				.thenReturn(Optional.of(emptyUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		ArgumentCaptor<BatchTranslationPrompt> promptCaptor = ArgumentCaptor.forClass(BatchTranslationPrompt.class);
		verify(translationLLMService).suggestBatchTranslations(eq(translationSet), promptCaptor.capture());
		assertThat(promptCaptor.getValue().promptLines()).containsExactly("1|Heart failure");
	}

	@Test
	void buildBatchPrompt_deduplicatesSharedContextAcrossBatch() {
		String setCode = "test-set";
		TranslationUnit contextA = unit("100", 0, setCode, TranslationStatus.APPROVED, List.of("Asma"));
		TranslationUnit emptyB = shellUnit("200", 1, setCode);
		TranslationUnit emptyC = shellUnit("300", 2, setCode);
		List<TranslationUnit> ordered = List.of(contextA, emptyB, emptyC);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"200", new TranslationSource("200", "Bronchitis", 1),
				"300", new TranslationSource("300", "Heart failure", 2));

		BatchTranslationPrompt prompt = SnolateBatchTranslationService.buildBatchPrompt(
				ordered, List.of(1, 2), sources);

		assertThat(prompt.promptLines()).containsExactly(
				"1|Asthma → Asma",
				"2|Bronchitis",
				"3|Heart failure");
	}

	private static TranslationUnit shellUnit(String code, int order, String setCode) {
		return new TranslationUnit(
				new TranslationUnit.MembershipKey(code, REFSET, LANG, COMPOSITE, order),
				List.of(), TranslationStatus.NOT_STARTED, new LinkedHashSet<>(Set.of(setCode)));
	}

	private static TranslationUnit unit(String code, int order, String setCode, TranslationStatus status, List<String> terms) {
		return new TranslationUnit(
				new TranslationUnit.MembershipKey(code, REFSET, LANG, COMPOSITE, order),
				terms, status, new LinkedHashSet<>(Set.of(setCode)));
	}
}
