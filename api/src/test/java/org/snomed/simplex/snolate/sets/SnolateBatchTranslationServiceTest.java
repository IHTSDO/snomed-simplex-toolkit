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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
	private SnolateTranslationUnitStore translationUnitStore;
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
				org.mockito.Mockito.mock(SnolateTranslationUnitRepository.class),
				translationUnitStore,
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
		mockEligibleUnits(setCode, List.of(emptyUnit), 1);
		when(translationSearchService.findAcceptedContextUnitsBeforeOrder(setCode, COMPOSITE, 0))
				.thenReturn(List.of());
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Asthma", List.of("Asma")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("100")))
				.thenReturn(Map.of("100", emptyUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		ArgumentCaptor<List<TranslationUnit>> captor = ArgumentCaptor.forClass(List.class);
		verify(translationUnitStore).saveAll(captor.capture());
		TranslationUnit saved = captor.getValue().get(0);
		assertThat(saved.getTerms()).isEmpty();
		assertThat(saved.getAiSuggestions()).containsExactly("Asma");
		assertThat(saved.getStatus()).isEqualTo(TranslationStatus.NOT_STARTED);
	}

	@Test
	void doRunAiBatchTranslate_skipsUnitsWithExistingSuggestions() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		when(translationSearchService.listEligibleUnitsForBatchTranslate(setCode, COMPOSITE, 1))
				.thenReturn(List.of());

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		verify(translationLLMService, never()).suggestBatchTranslations(any(), any());
		verify(translationUnitStore, never()).saveAll(any());
	}

	@Test
	void doRunAiBatchTranslate_includesAcceptedContextInPrompt() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit contextA = unit("100", 0, setCode, TranslationStatus.APPROVED, List.of("Asma"));
		TranslationUnit contextB = unit("200", 1, setCode, TranslationStatus.COMPLETE, List.of("Diabetes"));
		TranslationUnit emptyUnit = shellUnit("300", 2, setCode);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"200", new TranslationSource("200", "Diabetes mellitus", 1),
				"300", new TranslationSource("300", "Heart failure", 2));

		mockEligibleUnits(setCode, List.of(emptyUnit), 1);
		when(translationSearchService.findAcceptedContextUnitsBeforeOrder(setCode, COMPOSITE, 2))
				.thenReturn(List.of(contextA, contextB));
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			return codes.stream().map(sources::get).toList();
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Heart failure", List.of("Insuficiencia cardíaca")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("300")))
				.thenReturn(Map.of("300", emptyUnit));

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
		TranslationUnit emptyUnit = shellUnit("300", 2, setCode);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"300", new TranslationSource("300", "Heart failure", 2));

		mockEligibleUnits(setCode, List.of(emptyUnit), 1);
		when(translationSearchService.findAcceptedContextUnitsBeforeOrder(setCode, COMPOSITE, 2))
				.thenReturn(List.of(contextA));
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			return codes.stream().map(sources::get).toList();
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Heart failure", List.of("Insuficiencia cardíaca")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("300")))
				.thenReturn(Map.of("300", emptyUnit));

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
		TranslationUnit emptyUnit = shellUnit("200", 1, setCode);
		Map<String, TranslationSource> sources = Map.of(
				"200", new TranslationSource("200", "Heart failure", 1));

		mockEligibleUnits(setCode, List.of(emptyUnit), 1);
		when(translationSearchService.findAcceptedContextUnitsBeforeOrder(setCode, COMPOSITE, 1))
				.thenReturn(List.of());
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			return codes.stream().map(sources::get).toList();
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Heart failure", List.of("Insuficiencia cardíaca")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("200")))
				.thenReturn(Map.of("200", emptyUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		ArgumentCaptor<BatchTranslationPrompt> promptCaptor = ArgumentCaptor.forClass(BatchTranslationPrompt.class);
		verify(translationLLMService).suggestBatchTranslations(eq(translationSet), promptCaptor.capture());
		assertThat(promptCaptor.getValue().promptLines()).containsExactly("1|Heart failure");
	}

	@Test
	void doRunAiBatchTranslate_loadsOnlyBatchSources() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit eligibleUnit = shellUnit("0", 0, setCode);
		when(translationSearchService.listEligibleUnitsForBatchTranslate(setCode, COMPOSITE, 1))
				.thenReturn(List.of(eligibleUnit));
		when(translationSearchService.findAcceptedContextUnitsBeforeOrder(setCode, COMPOSITE, 0))
				.thenReturn(List.of());
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Iterable<String> idIterable = (Iterable<String>) inv.getArgument(0);
			List<TranslationSource> sources = new ArrayList<>();
			for (String code : idIterable) {
				sources.add(new TranslationSource(code, "Term " + code, Integer.parseInt(code)));
			}
			return sources;
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Term 0", List.of("Término 0")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("0")))
				.thenReturn(Map.of("0", eligibleUnit));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(1));

		verify(translationSearchService).listEligibleUnitsForBatchTranslate(setCode, COMPOSITE, 1);
		ArgumentCaptor<Iterable<String>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationSourceRepository, times(1)).findAllById(captor.capture());
		int loadedCodes = 0;
		for (String ignored : captor.getValue()) {
			loadedCodes++;
		}
		assertThat(loadedCodes).isEqualTo(1);
		verify(translationLLMService).suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class));
	}

	@Test
	void doRunAiBatchTranslate_reQueriesFirstEligiblePageAfterEachBatch() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit unitA = shellUnit("0", 0, setCode);
		TranslationUnit unitB = shellUnit("1", 1, setCode);
		AtomicInteger queryCount = new AtomicInteger();

		when(translationSearchService.listEligibleUnitsForBatchTranslate(eq(setCode), eq(COMPOSITE), anyInt()))
				.thenAnswer(invocation -> {
					int call = queryCount.incrementAndGet();
					if (call == 1) {
						assertThat(invocation.getArgument(2, Integer.class)).isEqualTo(2);
						return List.of(unitA);
					}
					if (call == 2) {
						assertThat(invocation.getArgument(2, Integer.class)).isEqualTo(1);
						return List.of(unitB);
					}
					return List.of();
				});
		when(translationSearchService.findAcceptedContextUnitsBeforeOrder(any(), any(), anyInt()))
				.thenReturn(List.of());
		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Iterable<String> idIterable = (Iterable<String>) inv.getArgument(0);
			List<TranslationSource> sources = new ArrayList<>();
			for (String code : idIterable) {
				sources.add(new TranslationSource(code, "Term " + code, Integer.parseInt(code)));
			}
			return sources;
		});
		when(translationLLMService.suggestBatchTranslations(eq(translationSet), any(BatchTranslationPrompt.class)))
				.thenReturn(Map.of("Term 0", List.of("Término 0")))
				.thenReturn(Map.of("Term 1", List.of("Término 1")));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("0")))
				.thenReturn(Map.of("0", unitA));
		when(translationUnitStore.loadByCodes(COMPOSITE, List.of("1")))
				.thenReturn(Map.of("1", unitB));

		service.doRunAiBatchTranslate(translationSet, new BatchTranslateRequest(2));

		verify(translationSearchService, times(2)).listEligibleUnitsForBatchTranslate(eq(setCode), eq(COMPOSITE), anyInt());
		verify(translationUnitStore, times(2)).saveAll(any());
	}

	@Test
	void buildBatchPrompt_deduplicatesSharedContextAcrossBatch() {
		String setCode = "test-set";
		TranslationUnit contextA = unit("100", 0, setCode, TranslationStatus.APPROVED, List.of("Asma"));
		TranslationUnit emptyB = shellUnit("200", 1, setCode);
		TranslationUnit emptyC = shellUnit("300", 2, setCode);
		Map<String, TranslationSource> sources = Map.of(
				"100", new TranslationSource("100", "Asthma", 0),
				"200", new TranslationSource("200", "Bronchitis", 1),
				"300", new TranslationSource("300", "Heart failure", 2));
		Map<String, List<TranslationUnit>> contextByCode = Map.of(
				"200", List.of(contextA),
				"300", List.of(contextA));

		BatchTranslationPrompt prompt = SnolateBatchTranslationService.buildBatchPrompt(
				List.of(emptyB, emptyC), sources, contextByCode);

		assertThat(prompt.promptLines()).containsExactly(
				"1|Asthma → Asma",
				"2|Bronchitis",
				"3|Heart failure");
	}

	private void mockEligibleUnits(String setCode, List<TranslationUnit> eligibleUnits, int limit) {
		when(translationSearchService.listEligibleUnitsForBatchTranslate(setCode, COMPOSITE, limit))
				.thenReturn(eligibleUnits);
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
