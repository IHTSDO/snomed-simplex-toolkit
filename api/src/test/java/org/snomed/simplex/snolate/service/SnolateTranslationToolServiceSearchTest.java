package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationUnitPage;
import org.snomed.simplex.rest.pojos.TranslationUnitRow;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationToolServiceSearchTest {

	private static final String LANG = "es";
	private static final String REFSET = "1000123";

	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;

	private SnolateTranslationToolService service;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationToolService(translationUnitRepository, translationSourceRepository,
				translationSearchService);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void getRows_withoutSearchUsesExistingPagingPath() throws ServiceExceptionWithStatusCode {
		TranslationUnit unit = unit("100", List.of("asma"));
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), isNull(), isNull(), isNull()))
				.thenReturn(pageOf(unit));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));

		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null, null, null);

		assertThat(result.count()).isEqualTo(1);
		assertThat(result.results()).hasSize(1);
		assertThat(result.results().get(0).getSource()).containsExactly("Asthma");
		verify(translationSearchService, never()).findSourceCodesByTermSubstring(any());
	}

	@Test
	void getRows_withEnglishSearchResolvesSourceCodes() throws ServiceExceptionWithStatusCode {
		when(translationSearchService.findSourceCodesByTermSubstring("asthma")).thenReturn(List.of("100"));
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), isNull(), eq(List.of("100")),
				isNull()))
				.thenReturn(pageOf(unit("100", List.of("asma"))));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));

		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null, "asthma", null);

		assertThat(result.results()).hasSize(1);
		verify(translationSearchService).findSourceCodesByTermSubstring("asthma");
	}

	@Test
	void getRows_withEmptyEnglishMatchReturnsNoRows() throws ServiceExceptionWithStatusCode {
		when(translationSearchService.findSourceCodesByTermSubstring("nomatch")).thenReturn(List.of());
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), isNull(), eq(List.of()),
				isNull()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null, "nomatch", null);

		assertThat(result.count()).isZero();
		assertThat(result.results()).isEmpty();
	}

	@Test
	void getRows_withConceptCodeSearch_usesExactCodeNotTermScan() throws ServiceExceptionWithStatusCode {
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), isNull(), eq(List.of("66379009")),
				isNull()))
				.thenReturn(pageOf(unit("66379009", List.of("asma"))));
		when(translationSourceRepository.findAllById(List.of("66379009")))
				.thenReturn(List.of(new TranslationSource("66379009", "Asthma", 0)));

		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null, "66379009", null);

		assertThat(result.results()).hasSize(1);
		assertThat(result.results().get(0).getContext()).isEqualTo("66379009");
		verify(translationSearchService, never()).findSourceCodesByTermSubstring(any());
	}

	@Test
	void getRows_withInvalidConceptCodeLength_returnsEmptyWithoutSearch() throws ServiceExceptionWithStatusCode {
		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null,
				"1234567890123456789", null);

		assertThat(result.count()).isZero();
		assertThat(result.results()).isEmpty();
		verify(translationSearchService, never()).findSourceCodesByTermSubstring(any());
		verify(translationSearchService, never()).pageUnitsInSet(any(), any(), any(), any(), any(), any());
	}

	@Test
	void getRows_withConceptCodeNoMatch_returnsEmpty() throws ServiceExceptionWithStatusCode {
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), isNull(), eq(List.of("99999999")),
				isNull()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

		TranslationUnitPage<TranslationUnitRow> result = service.getRows(translationSet, 0, 25, null, "99999999", null);

		assertThat(result.count()).isZero();
		assertThat(result.results()).isEmpty();
		verify(translationSearchService, never()).findSourceCodesByTermSubstring(any());
	}

	@Test
	void getRows_withTargetAndStatusFiltersPassesBothToSearchService() throws ServiceExceptionWithStatusCode {
		ArgumentCaptor<Collection<String>> englishCodesCaptor = ArgumentCaptor.forClass(Collection.class);
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), eq(TranslationStatus.APPROVED),
				englishCodesCaptor.capture(), eq("asma")))
				.thenReturn(pageOf(unit("100", List.of("asma"))));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));

		service.getRows(translationSet, 0, 25, TranslationStatus.APPROVED, null, "asma");

		assertThat(englishCodesCaptor.getValue()).isNull();
	}

	private static TranslationUnit unit(String code, List<String> terms) {
		TranslationUnit unit = new TranslationUnit(code, LANG + "-" + REFSET, terms, TranslationStatus.APPROVED);
		return unit;
	}

	private static Page<TranslationUnit> pageOf(TranslationUnit unit) {
		return new PageImpl<>(List.of(unit), PageRequest.of(0, 25), 1);
	}
}
