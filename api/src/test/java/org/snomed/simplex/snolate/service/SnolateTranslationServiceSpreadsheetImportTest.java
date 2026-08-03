package org.snomed.simplex.snolate.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationServiceSpreadsheetImportTest {

	private static final String LANG = "pt";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateTranslationUnitStore translationUnitStore;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;

	private SnolateTranslationService service;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationService(translationSourceRepository, translationSearchService, translationUnitStore);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void importTranslationSetFile_importsSpreadsheetWithFiveSynonymColumns() throws Exception {
		TranslationUnit unit = unit("100", List.of("old"), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("100", unit));

		byte[] spreadsheet = createSpreadsheet(
				List.of("Concept Code", "PT", "Synonym 1", "Synonym 2", "Synonym 3", "Synonym 4", "Synonym 5"),
				List.of("100", "asma", "asma brônquica", "asma alérgica", "asma extrínseca", "asma intrínseca", "asma persistente"));

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"Concept Code",
				List.of("PT", "Synonym 1", "Synonym 2", "Synonym 3", "Synonym 4", "Synonym 5"),
				TranslationStatus.FOR_REVIEW);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly(
				"asma", "asma brônquica", "asma alérgica", "asma extrínseca", "asma intrínseca", "asma persistente");
		assertThat(unit.getStatus()).isEqualTo(TranslationStatus.FOR_REVIEW);
		verify(translationUnitStore).saveAll(any());
	}

	@Test
	void importTranslationSetFile_importsSpreadsheetWithNumericConceptId() throws Exception {
		TranslationUnit unit = unit("195967001", List.of(), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("195967001", unit));

		byte[] spreadsheet = createSpreadsheetWithNumericConcept(
				List.of("Concept Code", "PT"),
				1.95967001E8,
				"asma");

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"Concept Code",
				List.of("PT"),
				TranslationStatus.APPROVED);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma");
		verify(translationUnitStore).saveAll(any());
	}

	@Test
	void importTranslationLanguageFile_importsSpreadsheet() throws Exception {
		TranslationUnit unit = unit("100", List.of("old"), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("100", unit));

		CodeSystem codeSystem = new CodeSystem("SNOMEDCT-TEST", "TEST", "");
		codeSystem.setTranslationSnolateLanguages(new java.util.HashMap<>(Map.of(REFSET, LANG)));

		byte[] spreadsheet = createSpreadsheet(
				List.of("Concept Code", "PT", "Synonym 1"),
				List.of("100", "asma", "asma brônquica"));

		ChangeSummary summary = service.importTranslationLanguageFile(
				codeSystem,
				REFSET,
				LANG,
				new ByteArrayInputStream(spreadsheet),
				"language.xlsx",
				"Concept Code",
				List.of("PT", "Synonym 1"),
				TranslationStatus.FOR_REVIEW);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma", "asma brônquica");
		verify(translationUnitStore).saveAll(any());
	}

	private static byte[] createSpreadsheet(List<String> headers, List<String> values) throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Row headerRow = workbook.createSheet().createRow(0);
			for (int i = 0; i < headers.size(); i++) {
				headerRow.createCell(i).setCellValue(headers.get(i));
			}
			Row dataRow = workbook.getSheetAt(0).createRow(1);
			for (int i = 0; i < values.size(); i++) {
				dataRow.createCell(i).setCellValue(values.get(i));
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static byte[] createSpreadsheetWithNumericConcept(List<String> headers, double conceptId, String term)
			throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Row headerRow = workbook.createSheet().createRow(0);
			for (int i = 0; i < headers.size(); i++) {
				headerRow.createCell(i).setCellValue(headers.get(i));
			}
			Row dataRow = workbook.getSheetAt(0).createRow(1);
			dataRow.createCell(0).setCellValue(conceptId);
			dataRow.createCell(1).setCellValue(term);
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private TranslationUnit unit(String code, List<String> terms, TranslationStatus status) {
		return new TranslationUnit(
				new TranslationUnit.MembershipKey(code, REFSET, LANG, COMPOSITE, 0),
				terms,
				status,
				new LinkedHashSet<>(Set.of(translationSet.getCompositeSetCode())));
	}
}
