package org.snomed.simplex.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapToSnomed;
import org.snomed.simplex.service.spreadsheet.HeaderConfiguration;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SimpleMapToSnomedRefsetServiceTest {

	@Mock
	private SpreadsheetService spreadsheetService;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	private SheetRowToComponentIntentExtractor<RefsetMemberIntentSimpleMapToSnomed> extractor;

	@BeforeEach
	void setUp() {
		SimpleMapToSnomedRefsetService service =
				new SimpleMapToSnomedRefsetService(spreadsheetService, snowstormClientFactory);
		extractor = service.getInputSheetMemberExtractor();
	}

	@Test
	void extractValidRow() throws Exception {
		HeaderConfiguration headers = headerConfiguration();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Row row = spreadsheetRow(workbook, "LOCAL-001", "123456789");

			RefsetMemberIntentSimpleMapToSnomed intent = extractor.extract(row, 2, headers);

			assertNotNull(intent);
			assertEquals("LOCAL-001", intent.getMapSource());
			assertEquals("123456789", intent.getReferenceComponentId());
		}
	}

	@Test
	void skipRowWhenSourceMissing() throws Exception {
		HeaderConfiguration headers = headerConfiguration();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Row row = spreadsheetRow(workbook, null, "123456789");

			assertNull(extractor.extract(row, 2, headers));
		}
	}

	@Test
	void skipRowWhenTargetMissing() throws Exception {
		HeaderConfiguration headers = headerConfiguration();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Row row = spreadsheetRow(workbook, "LOCAL-001", null);

			assertNull(extractor.extract(row, 2, headers));
		}
	}

	private static HeaderConfiguration headerConfiguration() {
		HeaderConfiguration headers = new HeaderConfiguration();
		headers.addHeader(new SheetHeader(SimpleMapServiceHelper.SOURCE_CODE), 0);
		headers.addHeader(new SheetHeader(SimpleMapServiceHelper.TARGET_CODE), 1);
		return headers;
	}

	private static Row spreadsheetRow(XSSFWorkbook workbook, String sourceCode, String targetCode) {
		XSSFSheet sheet = workbook.createSheet();
		Row row = sheet.createRow(1);
		if (sourceCode != null) {
			row.createCell(0).setCellValue(sourceCode);
		}
		if (targetCode != null) {
			row.createCell(1).setCellValue(targetCode);
		}
		return row;
	}
}
