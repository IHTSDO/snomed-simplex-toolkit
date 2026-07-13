package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationStatusLabels;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationToolServiceCsvExportTest {

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
	void displayLanguageDialect_stripsLanguageRefsetSuffix() {
		assertThat(SnolateTranslationToolService.displayLanguageDialect("Spanish language reference set"))
				.isEqualTo("Spanish");
		assertThat(SnolateTranslationToolService.displayLanguageDialect("French language refset"))
				.isEqualTo("French");
		assertThat(SnolateTranslationToolService.displayLanguageDialect("  "))
				.isEqualTo("Translation");
	}

	@Test
	void escapeCsvField_quotesFieldsWithSpecialCharacters() {
		assertThat(SnolateTranslationToolService.escapeCsvField("plain")).isEqualTo("plain");
		assertThat(SnolateTranslationToolService.escapeCsvField("a,b")).isEqualTo("\"a,b\"");
		assertThat(SnolateTranslationToolService.escapeCsvField("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(SnolateTranslationToolService.escapeCsvField("line1\nline2")).isEqualTo("\"line1\nline2\"");
	}

	@Test
	void writeTranslationSetCsv_writesHeaderAndMappedRows() throws Exception {
		TranslationUnit unit = unit("100", List.of("asma", "asma crónica"), TranslationStatus.FOR_REVIEW);
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), eq(TranslationStatus.FOR_REVIEW),
				isNull(), isNull()))
				.thenReturn(pageOf(unit));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		service.writeTranslationSetCsv(translationSet, TranslationStatus.FOR_REVIEW, "Spanish", out);

		String csv = out.toString(StandardCharsets.UTF_8);
		assertThat(csv).startsWith(
				"Concept Code,English Term,Spanish Preferred Term,Other Spanish Terms,Status,URL\n");
		assertThat(csv).contains(
				"100,Asthma,asma,asma crónica,Ready for review,https://snomed.info/id/100\n");
	}

	@Test
	void writeTranslationSetCsv_passesStatusFilterToGetRows() throws Exception {
		when(translationSearchService.pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), any(Pageable.class), eq(TranslationStatus.APPROVED),
				isNull(), isNull()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2000), 0));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		service.writeTranslationSetCsv(translationSet, TranslationStatus.APPROVED, "Spanish", out);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(translationSearchService).pageUnitsInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), pageableCaptor.capture(),
				eq(TranslationStatus.APPROVED), isNull(), isNull());
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2000);
	}

	@Test
	void translationStatusLabels_matchUiRadioLabels() {
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.FOR_REVIEW)).isEqualTo("Ready for review");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.NOT_STARTED)).isEqualTo("Not started");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.NEEDS_EDIT)).isEqualTo("Needs editing");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.APPROVED)).isEqualTo("Ready to push");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.COMPLETE)).isEqualTo("Pushed");
		assertThat(TranslationStatusLabels.exportFilenameSlug(TranslationStatus.FOR_REVIEW)).isEqualTo("ready-for-review");
		assertThat(TranslationStatusLabels.exportFilenameSlug(null)).isEqualTo("all-concepts");
	}

	private static TranslationUnit unit(String code, List<String> terms, TranslationStatus status) {
		return new TranslationUnit(code, LANG + "-" + REFSET, terms, status);
	}

	private static Page<TranslationUnit> pageOf(TranslationUnit unit) {
		return new PageImpl<>(List.of(unit), PageRequest.of(0, 2000), 1);
	}
}
