package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.TranslationLLMService;
import org.snomed.simplex.translation.tool.TranslationSetStatus;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnolateSetCreationServiceTest {

	@Test
	void doCreateSet_addsMemberOfOnUnitsForExistingSources() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 2) {
			@Override
			protected SnolateSetCreationService.ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet,
					SnowstormClientFactory factory) {
				List<String> ids = List.of("10", "20", "30");
				return new ConceptIdSource() {
					private int i;

					@Override
					public String next() {
						return i < ids.size() ? ids.get(i++) : null;
					}

					@Override
					public int getTotal() {
						return ids.size();
					}
				};
			}
		};

		TranslationSource s10 = new TranslationSource("10", "Ten", 0);
		TranslationSource s20 = new TranslationSource("20", "Twenty", 1);
		TranslationSource s30 = new TranslationSource("30", "Thirty", 2);

		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Iterable<String> idIterable = (Iterable<String>) inv.getArgument(0);
			List<String> codes = new ArrayList<>();
			idIterable.forEach(codes::add);
			List<TranslationSource> out = new ArrayList<>();
			for (String c : codes) {
				if ("10".equals(c)) {
					out.add(s10);
				} else if ("20".equals(c)) {
					out.add(s20);
				} else if ("30".equals(c)) {
					out.add(s30);
				}
			}
			return out;
		});

		when(translationUnitStore.loadByCodes(any(), any())).thenReturn(Map.of());

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-XS", "100", "Subset", "my-label", "<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-XS");
		set.setLanguageCode("en");
		set.setId("es-id");
		service.doCreateSet(set, snowstormClientFactory);

		String composite = "XS_100_my-label";
		verify(translationUnitStore, atLeastOnce()).saveAll(any());
		verify(snolateSetRepository, atLeastOnce()).save(any(SnolateTranslationSet.class));
	}

	@Test
	void doCreateSet_sizeMatchesAddedUnitsWhenSourcesMissing() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10) {
			@Override
			protected SnolateSetCreationService.ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet,
					SnowstormClientFactory factory) {
				List<String> ids = List.of("10", "20", "30");
				return new ConceptIdSource() {
					private int i;

					@Override
					public String next() {
						return i < ids.size() ? ids.get(i++) : null;
					}

					@Override
					public int getTotal() {
						return ids.size();
					}
				};
			}
		};

		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Iterable<String> idIterable = (Iterable<String>) inv.getArgument(0);
			List<String> codes = new ArrayList<>();
			idIterable.forEach(codes::add);
			List<TranslationSource> out = new ArrayList<>();
			for (String c : codes) {
				if ("10".equals(c)) {
					out.add(new TranslationSource("10", "Ten", 0));
				} else if ("20".equals(c)) {
					out.add(new TranslationSource("20", "Twenty", 1));
				}
			}
			return out;
		});
		when(translationUnitStore.loadByCodes(any(), any())).thenReturn(Map.of());
		when(translationSearchService.countUnitsInSet("XS_100_my-label", "en-100")).thenReturn(2L);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-XS", "100", "Subset", "my-label", "<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-XS");
		set.setLanguageCode("en");
		set.setId("es-id");
		service.doCreateSet(set, snowstormClientFactory);

		assertThat(set.getSize()).isEqualTo(2);
	}

	@Test
	void doRefreshSet_sizeMatchesMembershipWhenAddsSkipped() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		String composite = "ZS_200_z";
		String lang = "en-200";

		TranslationUnit hadOnly = TranslationUnit.shellMember("1", "200", "en", lang, 0, composite);
		TranslationUnit stays = TranslationUnit.shellMember("2", "200", "en", lang, 1, composite);

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			consumer.accept(hadOnly);
			consumer.accept(stays);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(composite), eq(lang), any());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10) {
			@Override
			protected SnolateSetCreationService.ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet,
					SnowstormClientFactory factory) {
				Set<String> eclResult = Set.of("2", "3");
				return new ConceptIdSource() {
					private final List<String> list = new ArrayList<>(eclResult);
					private int i;

					@Override
					public String next() {
						return i < list.size() ? list.get(i++) : null;
					}

					@Override
					public int getTotal() {
						return list.size();
					}
				};
			}
		};

		when(translationSourceRepository.findAllById(any())).thenReturn(List.of());
		when(translationUnitStore.loadByCodes(eq(lang), any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Collection<String> codes = inv.getArgument(1);
			Map<String, TranslationUnit> map = new HashMap<>();
			if (codes.contains("1")) {
				map.put("1", hadOnly);
			}
			if (codes.contains("2")) {
				map.put("2", stays);
			}
			return map;
		});
		when(translationSearchService.countUnitsInSet(composite, lang)).thenReturn(1L);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-ZS", "200", "Z", "z", "*", TranslationSubsetType.ECL, "SNOMEDCT-ZS");
		set.setLanguageCode("en");
		set.setId("id");
		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(set.getSize()).isEqualTo(1);
	}

	@Test
	void doRefreshSet_addsAndRemovesSetMembershipOnUnits() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		String composite = "ZS_200_z";
		String lang = "en-200";

		TranslationUnit hadOnly = TranslationUnit.shellMember("1", "200", "en", lang, 0, composite);
		TranslationUnit stays = TranslationUnit.shellMember("2", "200", "en", lang, 1, composite);
		TranslationSource willGain = new TranslationSource("3", "c", 2);

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			consumer.accept(hadOnly);
			consumer.accept(stays);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(composite), eq(lang), any());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10) {
			@Override
			protected SnolateSetCreationService.ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet,
					SnowstormClientFactory factory) {
				Set<String> eclResult = Set.of("2", "3");
				return new ConceptIdSource() {
					private final List<String> list = new ArrayList<>(eclResult);
					private int i;

					@Override
					public String next() {
						return i < list.size() ? list.get(i++) : null;
					}

					@Override
					public int getTotal() {
						return list.size();
					}
				};
			}
		};

		when(translationSourceRepository.findAllById(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Iterable<String> idIterable = (Iterable<String>) inv.getArgument(0);
			List<String> codes = new ArrayList<>();
			idIterable.forEach(codes::add);
			List<TranslationSource> out = new ArrayList<>();
			for (String c : codes) {
				if ("3".equals(c)) {
					out.add(willGain);
				}
			}
			return out;
		});

		when(translationUnitStore.loadByCodes(eq(lang), any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			Collection<String> codes = inv.getArgument(1);
			Map<String, TranslationUnit> map = new HashMap<>();
			if (codes.contains("1")) {
				map.put("1", hadOnly);
			}
			if (codes.contains("2")) {
				map.put("2", stays);
			}
			return map;
		});
		when(translationSearchService.countUnitsInSet(composite, lang)).thenReturn(2L);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-ZS", "200", "Z", "z", "*", TranslationSubsetType.ECL, "SNOMEDCT-ZS");
		set.setLanguageCode("en");
		set.setId("id");
		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(hadOnly.getMemberOf()).doesNotContain(composite);
		assertThat(stays.getMemberOf()).contains(composite);
		assertThat(willGain).isNotNull();
		verify(translationUnitStore, atLeastOnce()).saveAll(any());
	}

	@Test
	void doRefreshSet_syncsOrderForStayingMembers() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		String composite = "ZS_200_z";
		String lang = "en-200";
		TranslationUnit stays = TranslationUnit.shellMember("2", "200", "en", lang, 0, composite);

		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			consumer.accept(stays);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(composite), eq(lang), any());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10) {
			@Override
			protected SnolateSetCreationService.ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet,
					SnowstormClientFactory factory) {
				return new ConceptIdSource() {
					private boolean consumed;

					@Override
					public String next() {
						if (consumed) {
							return null;
						}
						consumed = true;
						return "2";
					}

					@Override
					public int getTotal() {
						return 1;
					}
				};
			}
		};

		when(translationSourceRepository.findAllById(List.of("2")))
				.thenReturn(List.of(new TranslationSource("2", "Staying", 99)));
		when(translationSearchService.countUnitsInSet(composite, lang)).thenReturn(1L);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-ZS", "200", "Z", "z", "*", TranslationSubsetType.ECL, "SNOMEDCT-ZS");
		set.setLanguageCode("en");
		set.setId("id");
		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(stays.getOrder()).isEqualTo(99);
		verify(translationUnitStore).saveAll(List.of(stays));
	}

	@Test
	void refreshSetForUpgrade_setsQueuedForUpgradeAndQueuesJob() throws ServiceException {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test-user", "n/a"));

		SnolateSetRepository snolateSetRepository = mock();
		JmsTemplate jmsTemplate = mock();
		SnowstormClientFactory snowstormClientFactory = mock();
		SnowstormClient snowstormClient = mock();
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDependantVersionEffectiveTime(20250701);

		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				mock(), mock(), mock(), mock(), mock(TranslationLLMService.class),
				new HashMap<>(), jmsTemplate, "test-queue", new ObjectMapper());
		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "Subset", "my-label", "<<404684003",
				TranslationSubsetType.ECL, "SNOMEDCT-TEST");
		set.setId("set-id");
		set.setStatus(TranslationSetStatus.READY);
		set.setInternationalEffectiveTime(20240101);

		service.refreshSetForUpgrade(set);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.QUEUED_FOR_UPGRADE);
		assertThat(set.getInternationalEffectiveTime()).isEqualTo(20250701);
		verify(snolateSetRepository).save(set);
		verify(jmsTemplate).convertAndSend(eq("test-queue"), anyMap());

		SecurityContextHolder.clearContext();
	}

	@Test
	void refreshSetForUpgrade_rejectsBusySet() {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateProcessingContext ctx = new SnolateProcessingContext(mock(), snolateSetRepository,
				mock(), mock(), mock(), mock(), mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());
		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "Subset", "my-label", "<<404684003",
				TranslationSubsetType.ECL, "SNOMEDCT-TEST");
		set.setStatus(TranslationSetStatus.UPGRADING);

		assertThatThrownBy(() -> service.refreshSetForUpgrade(set))
				.isInstanceOf(ServiceExceptionWithStatusCode.class);
	}

	@Test
	void doRefreshSet_fromQueuedForUpgrade_usesUpgradingStatus() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		String composite = "ZS_200_z";
		String lang = "en-200";

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10) {
			@Override
			protected SnolateSetCreationService.ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet,
					SnowstormClientFactory factory) {
				return new ConceptIdSource() {
					@Override
					public String next() {
						return null;
					}

					@Override
					public int getTotal() {
						return 0;
					}
				};
			}
		};

		doAnswer(invocation -> null).when(translationSearchService).forEachUnitInSet(eq(composite), eq(lang), any());
		when(translationSearchService.countUnitsInSet(composite, lang)).thenReturn(0L);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-ZS", "200", "Z", "z", "*", TranslationSubsetType.ECL, "SNOMEDCT-ZS");
		set.setLanguageCode("en");
		set.setId("id");
		set.setStatus(TranslationSetStatus.QUEUED_FOR_UPGRADE);

		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.READY);
		verify(snolateSetRepository, atLeastOnce()).save(any(SnolateTranslationSet.class));
	}

	@Test
	void doCreateSet_usesStoredConceptListForConceptListSubsetType() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10);

		TranslationSource source = new TranslationSource("111111111", "Term", 0);
		when(translationSourceRepository.findAllById(any())).thenReturn(List.of(source));
		when(translationUnitStore.loadByCodes(any(), any())).thenReturn(Map.of());
		when(translationSearchService.countUnitsInSet(any(), any())).thenReturn(1L);

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-XS", "100", "File set", "file-set",
				SnolateSetCreationService.CONCEPT_LIST_ECL_PLACEHOLDER, TranslationSubsetType.CONCEPT_LIST, "SNOMEDCT-XS");
		set.setConceptList("111111111,222222222");
		set.setLanguageCode("en");
		set.setId("file-set-id");

		service.doCreateSet(set, snowstormClientFactory);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.READY);
		verify(translationUnitStore, atLeastOnce()).saveAll(any());
	}

	@Test
	void doRefreshSet_reappliesStoredConceptList() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationUnitStore translationUnitStore = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationUnitStore, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		SnolateSetCreationService service = new SnolateSetCreationService(ctx, 10);

		String composite = "XS_100_file-set";
		String lang = "en-100";
		when(translationSearchService.countUnitsInSet(composite, lang)).thenReturn(1L);
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(2);
			TranslationUnit existing = new TranslationUnit();
			existing.setCode("111111111");
			consumer.accept(existing);
			return null;
		}).when(translationSearchService).forEachUnitInSet(eq(composite), eq(lang), any());

		TranslationSource source = new TranslationSource("222222222", "Term", 0);
		when(translationSourceRepository.findAllById(any())).thenReturn(List.of(source));
		when(translationUnitStore.loadByCodes(any(), any())).thenReturn(Map.of());

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-XS", "100", "File set", "file-set",
				SnolateSetCreationService.CONCEPT_LIST_ECL_PLACEHOLDER, TranslationSubsetType.CONCEPT_LIST, "SNOMEDCT-XS");
		set.setConceptList("222222222");
		set.setLanguageCode("en");
		set.setId("file-set-id");
		set.setStatus(TranslationSetStatus.READY);

		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.READY);
		verify(translationUnitStore, atLeastOnce()).saveAll(any());
	}
}
