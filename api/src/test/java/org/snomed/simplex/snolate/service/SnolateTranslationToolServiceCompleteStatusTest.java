package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationToolServiceCompleteStatusTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

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
	void updateTranslationUnit_rejectsCompleteOnNonCompleteUnit() {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit unit = new TranslationUnit("100", REFSET, LANG, COMPOSITE, 0, List.of("term"),
				TranslationStatus.FOR_REVIEW, new LinkedHashSet<>(Set.of(setCode)));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("100", COMPOSITE))
				.thenReturn(Optional.of(unit));

		assertThatThrownBy(() -> service.updateTranslationUnit(translationSet, "100", List.of("term"),
				TranslationStatus.COMPLETE))
				.isInstanceOf(ServiceExceptionWithStatusCode.class)
				.hasMessageContaining("COMPLETE is set automatically");
	}

	@Test
	void updateTranslationUnit_allowsIdempotentCompleteSave() throws Exception {
		String setCode = translationSet.getCompositeSetCode();
		TranslationUnit unit = new TranslationUnit("100", REFSET, LANG, COMPOSITE, 0, List.of("term"),
				TranslationStatus.COMPLETE, new LinkedHashSet<>(Set.of(setCode)));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("100", COMPOSITE))
				.thenReturn(Optional.of(unit));

		service.updateTranslationUnit(translationSet, "100", List.of("term"), TranslationStatus.COMPLETE);

		assertThat(unit.getStatus()).isEqualTo(TranslationStatus.COMPLETE);
		verify(translationUnitRepository).save(unit);
	}
}
