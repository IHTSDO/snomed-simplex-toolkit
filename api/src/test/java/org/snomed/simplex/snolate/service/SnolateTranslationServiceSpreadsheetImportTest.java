package org.snomed.simplex.snolate.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
	void importTranslationSetFile_mergesDuplicateConceptRows_firstRowIsPt() throws Exception {
		TranslationUnit unit = unit("100", List.of("old"), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("100", unit));

		byte[] spreadsheet = createSpreadsheetWithDataRows(
				List.of("Concept Code", "PT"),
				List.of(
						List.of("100", "asma"),
						List.of("100", "asma brônquica"),
						List.of("100", "asma")));

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"Concept Code",
				List.of("PT"),
				TranslationStatus.FOR_REVIEW);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma", "asma brônquica");
		verify(translationUnitStore).saveAll(any());
	}

	@Test
	void importTranslationSetFile_importsSpreadsheetWithPreambleRows() throws Exception {
		TranslationUnit unit = unit("100", List.of(), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("100", unit));

		byte[] spreadsheet = createSpreadsheetWithHeaderAtRow(
				5,
				List.of("Concept Code", "PT"),
				List.of("100", "asma"));

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"Concept Code",
				List.of("PT"),
				TranslationStatus.APPROVED,
				OutsideSetBehavior.SKIP,
				null,
				5);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma");
		verify(translationUnitStore).saveAll(any());
	}

	@Test
	void importTranslationSetFile_importsFromNamedSheet() throws Exception {
		TranslationUnit unit = unit("100", List.of(), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("100", unit));

		byte[] spreadsheet = createMultiSheetSpreadsheet(
				"Notes",
				List.of(List.of("Notes only")),
				"Data",
				List.of("Concept Code", "PT"),
				List.of("100", "asma"));

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"Concept Code",
				List.of("PT"),
				TranslationStatus.FOR_REVIEW,
				OutsideSetBehavior.SKIP,
				"Data",
				0);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma");
		verify(translationUnitStore).saveAll(any());
	}

	@Test
	void importTranslationSetFile_recordsSkippedNotFoundCodes() throws Exception {
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of());

		byte[] spreadsheet = createSpreadsheet(
				List.of("Concept Code", "PT"),
				List.of("999", "unknown term"));

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"Concept Code",
				List.of("PT"),
				TranslationStatus.FOR_REVIEW);

		assertThat(summary.getUpdated()).isZero();
		assertThat(summary.getSkippedNotFound()).isEqualTo(1);
		assertThat(summary.getSkippedNotFoundCodes()).containsExactly("999");
	}

	@Test
	void detectHeaderRowIndex_skipsTitleRowsBeforeHeaders() throws Exception {
		byte[] spreadsheet = createSpreadsheetWithHeaderAtRow(
				3,
				List.of("Concept Code", "PT"),
				List.of("100", "asma"));

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(SnolateTranslationService.detectHeaderRowIndex(sheet, 10)).isEqualTo(3);
		}
	}

	@Test
	void detectHeaderRowIndex_handlesHeaderWiderThanSparseDataRows() throws Exception {
		byte[] spreadsheet = createAnatomijaPatternSpreadsheet();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(SnolateTranslationService.detectHeaderRowIndex(sheet, 10)).isEqualTo(2);
		}
	}

	@Test
	void importTranslationSetFile_importsSpreadsheetWithSparseOptionalColumn() throws Exception {
		TranslationUnit unit = unit("32713005", List.of(), TranslationStatus.NOT_STARTED);
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenReturn(Map.of("32713005", unit));

		byte[] spreadsheet = createAnatomijaPatternSpreadsheet();

		ChangeSummary summary = service.importTranslationSetFile(
				translationSet,
				new ByteArrayInputStream(spreadsheet),
				"translations.xlsx",
				"SNOMED CT code",
				List.of("Latviskais termins"),
				TranslationStatus.FOR_REVIEW,
				OutsideSetBehavior.SKIP,
				null,
				2);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("Aklā zarna");
		verify(translationUnitStore).saveAll(any());
	}

	private static byte[] createAnatomijaPatternSpreadsheet() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("01_Anatomija");
			sheet.createRow(0).createCell(0).setCellValue("Finding location / Atradnes lokācija");
			sheet.createRow(1).createCell(0).setCellValue("Lokālais value set ID: COLONO-LOC-001");
			Row headerRow = sheet.createRow(2);
			List<String> headers = List.of(
					"Value set ID", "Field / lauks", "SNOMED CT code", "Preferred term (EN)",
					"Concept type / Jēdziena tips", "Interface term (EN)", "Latviskais termins",
					"Statuss", "Piezīmes", "Avots");
			for (int i = 0; i < headers.size(); i++) {
				headerRow.createCell(i).setCellValue(headers.get(i));
			}
			Row dataRow = sheet.createRow(3);
			List<String> values = List.of(
					"COLONO-LOC-001", "Finding location / Atradnes lokācija", "32713005", "Cecum structure",
					"body structure", "Cecum", "Aklā zarna", "Iekļauts", "", "https://browser.ihtsdotools.org/");
			for (int i = 0; i < values.size(); i++) {
				dataRow.createCell(i).setCellValue(values.get(i));
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static byte[] createSpreadsheetWithHeaderAtRow(int headerRowIndex, List<String> headers, List<String> values)
			throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet();
			for (int rowIndex = 0; rowIndex < headerRowIndex; rowIndex++) {
				sheet.createRow(rowIndex).createCell(0).setCellValue("Title row " + (rowIndex + 1));
			}
			Row headerRow = sheet.createRow(headerRowIndex);
			for (int i = 0; i < headers.size(); i++) {
				headerRow.createCell(i).setCellValue(headers.get(i));
			}
			Row dataRow = sheet.createRow(headerRowIndex + 1);
			for (int i = 0; i < values.size(); i++) {
				dataRow.createCell(i).setCellValue(values.get(i));
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static byte[] createMultiSheetSpreadsheet(String firstSheetName, List<List<String>> firstSheetRows,
			String secondSheetName, List<String> headers, List<String> values) throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet firstSheet = workbook.createSheet(firstSheetName);
			for (int rowIndex = 0; rowIndex < firstSheetRows.size(); rowIndex++) {
				Row row = firstSheet.createRow(rowIndex);
				List<String> valuesForRow = firstSheetRows.get(rowIndex);
				for (int columnIndex = 0; columnIndex < valuesForRow.size(); columnIndex++) {
					row.createCell(columnIndex).setCellValue(valuesForRow.get(columnIndex));
				}
			}
			Sheet secondSheet = workbook.createSheet(secondSheetName);
			Row headerRow = secondSheet.createRow(0);
			for (int i = 0; i < headers.size(); i++) {
				headerRow.createCell(i).setCellValue(headers.get(i));
			}
			Row dataRow = secondSheet.createRow(1);
			for (int i = 0; i < values.size(); i++) {
				dataRow.createCell(i).setCellValue(values.get(i));
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static byte[] createSpreadsheetWithDataRows(List<String> headers, List<List<String>> dataRows)
			throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet();
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.size(); i++) {
				headerRow.createCell(i).setCellValue(headers.get(i));
			}
			for (int rowIndex = 0; rowIndex < dataRows.size(); rowIndex++) {
				Row dataRow = sheet.createRow(rowIndex + 1);
				List<String> values = dataRows.get(rowIndex);
				for (int i = 0; i < values.size(); i++) {
					dataRow.createCell(i).setCellValue(values.get(i));
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
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
