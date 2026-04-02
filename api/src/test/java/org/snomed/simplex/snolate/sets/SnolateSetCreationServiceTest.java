package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.repository.TranslationSourceRepository;
import org.snomed.simplex.snolate.repository.TranslationUnitRepository;
import org.snomed.simplex.translation.TranslationLLMService;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.jms.core.JmsTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnolateSetCreationServiceTest {

	@Test
	void doCreateSet_addsCompositeSetCodeToExistingSourcesAndTouchesElasticsearch() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		TranslationSourceRepository translationSourceRepository = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, mock(TranslationUnitRepository.class), mock(TranslationLLMService.class),
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

		when(translationSourceRepository.findAllByCodeInFetchingSets(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = (List<String>) inv.getArgument(0);
			List<TranslationSource> out = new ArrayList<>();
			if (codes.contains("10")) {
				out.add(s10);
			}
			if (codes.contains("20")) {
				out.add(s20);
			}
			return out;
		});

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-XS", "100", "Subset", "my-label", "<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-XS");
		set.setId("es-id");
		service.doCreateSet(set, snowstormClientFactory);

		String composite = "XS_100_my-label";
		assertThat(s10.getSets()).contains(composite);
		assertThat(s20.getSets()).contains(composite);

		verify(translationSourceRepository, atLeastOnce()).saveAll(any());
		verify(snolateSetRepository, atLeastOnce()).save(any(SnolateTranslationSet.class));
	}

	@Test
	void doRefreshSet_addsAndRemovesSetMembership() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		TranslationSourceRepository translationSourceRepository = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, mock(TranslationUnitRepository.class), mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		String composite = "ZS_200_z";

		TranslationSource hadOnly = new TranslationSource("1", "a", 0);
		hadOnly.getSets().add(composite);
		TranslationSource stays = new TranslationSource("2", "b", 1);
		stays.getSets().add(composite);
		TranslationSource willGain = new TranslationSource("3", "c", 2);

		when(translationSourceRepository.findAllHavingSetMembership(composite)).thenReturn(List.of(hadOnly, stays));

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

		when(translationSourceRepository.findAllByCodeInFetchingSets(any())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<String> codes = new ArrayList<>((List<String>) inv.getArgument(0));
			List<TranslationSource> out = new ArrayList<>();
			for (String c : codes) {
				if ("1".equals(c)) {
					out.add(hadOnly);
				} else if ("2".equals(c)) {
					out.add(stays);
				} else if ("3".equals(c)) {
					out.add(willGain);
				}
			}
			return out;
		});

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-ZS", "200", "Z", "z", "*", TranslationSubsetType.ECL, "SNOMEDCT-ZS");
		set.setId("id");
		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(hadOnly.getSets()).doesNotContain(composite);
		assertThat(stays.getSets()).contains(composite);
		assertThat(willGain.getSets()).contains(composite);
		verify(translationSourceRepository, atLeastOnce()).saveAll(any());
	}
}
