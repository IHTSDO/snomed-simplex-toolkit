package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.TranslationLLMService;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.jms.core.JmsTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnolateSetCreationServiceTest {

	@Test
	void doCreateSet_addsMemberOfOnUnitsForExistingSources() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationSearchService, mock(TranslationLLMService.class),
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

		when(translationUnitRepository.findByCodeAndCompositeLanguageCode(any(), any())).thenReturn(Optional.empty());

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-XS", "100", "Subset", "my-label", "<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-XS");
		set.setLanguageCode("en");
		set.setId("es-id");
		service.doCreateSet(set, snowstormClientFactory);

		String composite = "XS_100_my-label";
		verify(translationUnitRepository, atLeastOnce()).save(any(TranslationUnit.class));
		verify(snolateSetRepository, atLeastOnce()).save(any(SnolateTranslationSet.class));
	}

	@Test
	void doRefreshSet_addsAndRemovesSetMembershipOnUnits() throws ServiceExceptionWithStatusCode {
		SnolateSetRepository snolateSetRepository = mock();
		SnolateTranslationSourceRepository translationSourceRepository = mock();
		SnolateTranslationUnitRepository translationUnitRepository = mock();
		SnolateTranslationSearchService translationSearchService = mock();
		SnowstormClientFactory snowstormClientFactory = mock();

		SnolateProcessingContext ctx = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, translationUnitRepository, translationSearchService, mock(TranslationLLMService.class),
				new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());

		String composite = "ZS_200_z";
		String lang = "en-200";

		TranslationUnit hadOnly = TranslationUnit.shellMember("1", "200", "en", lang, 0, composite);
		TranslationUnit stays = TranslationUnit.shellMember("2", "200", "en", lang, 1, composite);
		TranslationSource willGain = new TranslationSource("3", "c", 2);

		when(translationSearchService.listAllUnitsInSet(composite, lang)).thenReturn(List.of(hadOnly, stays));

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

		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("1", lang)).thenReturn(Optional.of(hadOnly));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("2", lang)).thenReturn(Optional.of(stays));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("3", lang)).thenReturn(Optional.empty());

		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-ZS", "200", "Z", "z", "*", TranslationSubsetType.ECL, "SNOMEDCT-ZS");
		set.setLanguageCode("en");
		set.setId("id");
		service.doRefreshSet(set, snowstormClientFactory);

		assertThat(hadOnly.getMemberOf()).doesNotContain(composite);
		assertThat(stays.getMemberOf()).contains(composite);
		assertThat(willGain).isNotNull();
		verify(translationUnitRepository, atLeastOnce()).save(any(TranslationUnit.class));
	}
}
