package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationUnitPage;
import org.snomed.simplex.rest.pojos.TranslationUnitRow;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationServiceAiSuggestionsTest {

	private static final String LANG = "es";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;

	private SnolateTranslationService service;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationService(translationUnitRepository, translationSourceRepository,
				translationSearchService);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void getRows_includesAiSuggestions() throws ServiceExceptionWithStatusCode {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit unit = new TranslationUnit(
				new TranslationUnit.MembershipKey("100", REFSET, LANG, COMPOSITE, 0),
				List.of(), TranslationStatus.NOT_STARTED, new LinkedHashSet<>(Set.of(setCode)));
		unit.setAiSuggestions(List.of("Asma sugerida"));
		when(translationSearchService.pageUnitsInSet(eq(setCode), eq(COMPOSITE), any(Pageable.class), isNull(), isNull(), isNull()))
				.thenReturn(new PageImpl<>(List.of(unit), PageRequest.of(0, 25), 1));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));

		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null, null, null);

		assertThat(result.results()).hasSize(1);
		assertThat(result.results().get(0).getTarget()).isEmpty();
		assertThat(result.results().get(0).getSuggestions()).containsExactly("Asma sugerida");
	}

	@Test
	void updateTranslationUnit_clearsAiSuggestionsWhenAccepting() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit unit = new TranslationUnit(
				new TranslationUnit.MembershipKey("100", REFSET, LANG, COMPOSITE, 0),
				List.of(), TranslationStatus.NOT_STARTED, new LinkedHashSet<>(Set.of(setCode)));
		unit.setAiSuggestions(List.of("Asma sugerida"));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("100", COMPOSITE))
				.thenReturn(Optional.of(unit));

		service.updateTranslationUnit(translationSet, "100", List.of("Asma aceptada"), TranslationStatus.FOR_REVIEW);

		assertThat(unit.getTerms()).containsExactly("Asma aceptada");
		assertThat(unit.getStatus()).isEqualTo(TranslationStatus.FOR_REVIEW);
		assertThat(unit.getAiSuggestions()).isEmpty();
		verify(translationUnitRepository).save(unit);
	}

	@Test
	void updateTranslationUnit_clearsAiSuggestionsWhenResettingToNotStarted() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit unit = new TranslationUnit(
				new TranslationUnit.MembershipKey("100", REFSET, LANG, COMPOSITE, 0),
				List.of(), TranslationStatus.NOT_STARTED, new LinkedHashSet<>(Set.of(setCode)));
		unit.setAiSuggestions(List.of("Asma sugerida"));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("100", COMPOSITE))
				.thenReturn(Optional.of(unit));

		service.updateTranslationUnit(translationSet, "100", List.of(), TranslationStatus.NOT_STARTED);

		assertThat(unit.getTerms()).isEmpty();
		assertThat(unit.getAiSuggestions()).isEmpty();
		verify(translationUnitRepository).save(unit);
	}
}
