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
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapFromSnomed;
import org.snomed.simplex.service.spreadsheet.HeaderConfiguration;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SimpleMapFromSnomedRefsetServiceTest {

	@Mock
	private SpreadsheetService spreadsheetService;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	private SheetRowToComponentIntentExtractor<RefsetMemberIntentSimpleMapFromSnomed> extractor;

	@BeforeEach
	void setUp() {
		SimpleMapFromSnomedRefsetService service =
				new SimpleMapFromSnomedRefsetService(spreadsheetService, snowstormClientFactory);
		extractor = service.getInputSheetMemberExtractor();
	}

	@Test
	void extractValidRow() throws Exception {
		HeaderConfiguration headers = headerConfiguration();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Row row = spreadsheetRow(workbook, "123456789", "EXT-001");

			RefsetMemberIntentSimpleMapFromSnomed intent = extractor.extract(row, 2, headers);

			assertNotNull(intent);
			assertEquals("123456789", intent.getReferenceComponentId());
			assertEquals("EXT-001", intent.getMapTarget());
		}
	}

	@Test
	void skipRowWhenSourceMissing() throws Exception {
		HeaderConfiguration headers = headerConfiguration();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Row row = spreadsheetRow(workbook, null, "EXT-001");

			assertNull(extractor.extract(row, 2, headers));
		}
	}

	@Test
	void skipRowWhenTargetMissing() throws Exception {
		HeaderConfiguration headers = headerConfiguration();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Row row = spreadsheetRow(workbook, "123456789", null);

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
