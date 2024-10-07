package org.snomed.simplex.service;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.domain.ComponentIntent;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.spreadsheet.HeaderConfiguration;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.snomed.simplex.util.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.snomed.simplex.client.domain.Concepts.IS_A;

@Service
public class SpreadsheetService {

	public static final XSSFFont BOLD_FONT = new XSSFFont();
	public static final String NEW_LINE = "\n";
	public static final String SIMPLEX_BRANCH_TIMESTAMP_PREFIX = "Simplex branch timestamp:";

	static {
		BOLD_FONT.setBold(true);
	}

	public Workbook createRefsetSpreadsheet(List<RefsetMember> members, Map<String, Function<RefsetMember, String>> refsetColumns) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet();

		// Format all value cells as text.
		// To prevent them being automatically formatted as number because that can lead to formatting / rounding issues.
		CellStyle cellStyle = getCellStyle(workbook);

		int rowOffset = 0;
		Row headerRow = sheet.createRow(rowOffset++);
		int columnOffset = 0;
		for (String columnName : refsetColumns.keySet()) {
			Cell cell = headerRow.createCell(columnOffset++);
			XSSFRichTextString textString = new XSSFRichTextString(columnName);
			textString.applyFont(BOLD_FONT);
			cell.setCellValue(textString);
		}
		List<Row> dataRows = new ArrayList<>();
		for (RefsetMember member : members) {
			columnOffset = 0;
			Row row = sheet.createRow(rowOffset++);
			for (Map.Entry<String, Function<RefsetMember, String>> column : refsetColumns.entrySet()) {
				Cell cell = row.createCell(columnOffset++);
				cell.setCellStyle(cellStyle);
				cell.setCellValue(column.getValue().apply(member));
			}
			dataRows.add(row);
		}

		resizeColumns(sheet, dataRows);

		return workbook;
	}

	public Workbook createConceptSpreadsheet(List<SheetHeader> headers, List<Concept> concepts, List<ConceptMini> langRefsets, long contentHeadTimestamp) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet();
		addHeader(headers, contentHeadTimestamp, sheet);

		List<Row> dataRows = new ArrayList<>();
		// Format all value cells as text.
		// To prevent them being automatically formatted as number because that can lead to formatting / rounding issues.
		CellStyle cellStyle = getCellStyle(workbook);
		int rowOffset = 1;
		for (Concept concept : concepts) {
			rowOffset = addConceptRows(langRefsets, concept, sheet, rowOffset, cellStyle, dataRows);
		}

		resizeColumns(sheet, dataRows);

		return workbook;
	}

	private static void addHeader(List<SheetHeader> headers, long contentHeadTimestamp, Sheet sheet) {
		Row headerRow = sheet.createRow(0);
		headerRow.setHeight((short) (headerRow.getHeight() * 4));
		int columnOffset = 0;
		for (SheetHeader header : headers) {
			Cell cell = headerRow.createCell(columnOffset);
			XSSFRichTextString textString = new XSSFRichTextString();
			textString.append(header.getName(), BOLD_FONT);
			if (header.getSubtitle() != null) {
				textString.append("\n");
				textString.append(header.getSubtitle());
			}
			cell.setCellValue(textString);
			CellStyle headerCellStyle = sheet.getWorkbook().createCellStyle();
			headerCellStyle.setWrapText(true);
			headerCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cell.setCellStyle(headerCellStyle);
			columnOffset++;
		}

		columnOffset = 26 * 3;
		Cell timestampCell = headerRow.createCell(columnOffset);
		XSSFRichTextString textString = new XSSFRichTextString();
		textString.append((SIMPLEX_BRANCH_TIMESTAMP_PREFIX + "%s").formatted(contentHeadTimestamp));
		timestampCell.setCellValue(textString);
	}

	private static int addConceptRows(List<ConceptMini> langRefsets, Concept concept, Sheet sheet, int rowOffset, CellStyle cellStyle, List<Row> dataRows) {
		Row row = sheet.createRow(rowOffset++);

		// Parent Concept Identifier | Parent Concept Term | Concept Identifier | Active | Terms in English, US dialect | [0.*] Terms in X
		addParentIdAndTerm(concept, cellStyle, row);


		// add concept id
		int columnOffset = 2;
		Cell cell = row.createCell(columnOffset++);
		cell.setCellStyle(cellStyle);
		cell.setCellValue(concept.getConceptId());

		// add active
		cell = row.createCell(columnOffset++);
		cell.setCellStyle(cellStyle);
		cell.setCellValue(concept.isActive() ? "" : "false");

		// add terms
		List<Description> descriptions = concept.getDescriptions();
		List<String> langRefsetIds = new ArrayList<>();
		langRefsetIds.add(Concepts.US_LANG_REFSET);
		langRefsetIds.addAll(langRefsets.stream().map(ConceptMini::getConceptId).toList());
		Map<String, List<String>> descriptionsPerLangRefset = getDescriptionsPerLangRefset(descriptions, langRefsetIds);
		int maxTerms = descriptionsPerLangRefset.values().stream().map(List::size).max(Integer::compare).orElse(1);
		for (int i = 0; i < maxTerms; i++) {
			if (i > 0) {
				dataRows.add(row);
				row = sheet.createRow(rowOffset++);
			}
			int termColumnOffset = columnOffset;
			for (String langRefsetId : langRefsetIds) {
				List<String> terms = descriptionsPerLangRefset.get(langRefsetId);
				String term = "";
				if (terms.size() > i) {
					term = terms.get(i);
				}
				cell = row.createCell(termColumnOffset++);
				cell.setCellStyle(cellStyle);
				cell.setCellValue(term);
			}
		}
		dataRows.add(row);
		return rowOffset;
	}

	private static void addParentIdAndTerm(Concept concept, CellStyle cellStyle, Row row) {
		String parentId = "";
		String parentFSN = "";
		for (Axiom classAxiom : concept.getClassAxioms()) {
			for (Relationship relationship : classAxiom.getRelationships()) {
				if (relationship.getTypeId().equals(IS_A)) {
					parentId = relationship.getTarget().getConceptId();
					DescriptionMini fsn = relationship.getTarget().getFsn();
					parentFSN = fsn != null ? fsn.getTerm() : "";
				}
			}
		}

		Cell cell = row.createCell(0);
		cell.setCellStyle(cellStyle);
		cell.setCellValue(parentId);

		cell = row.createCell(1);
		cell.setCellStyle(cellStyle);
		cell.setCellValue(parentFSN);
	}

	public Workbook createValidationReportSpreadsheet(ValidationReport validationReport) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet();

		CellStyle cellStyle = getCellStyle(workbook);

		AtomicInteger rowOffset = new AtomicInteger(0);
		Row headerRow = sheet.createRow(rowOffset.getAndIncrement());
		int columnOffset = 0;
        for (Pair<String, Integer> headerColumn : List.of(Pair.of("Rule Type", 60_000), Pair.of("Concept Code", 30_00), Pair.of("Additional Detail", 65_000))) {
			Cell cell = headerRow.createCell(columnOffset);
			XSSFRichTextString textString = new XSSFRichTextString(headerColumn.getLeft());
			textString.applyFont(BOLD_FONT);
			cell.setCellValue(textString);
			cell.setCellStyle(cellStyle);
			sheet.setColumnWidth(columnOffset, headerColumn.getRight());
			columnOffset++;
		}

		ValidationReport.TestResult testResult = validationReport.rvfValidationResult().TestResult();
		int errors = testResult.totalFailures();
		int warnings = testResult.totalWarnings();

		Row messageRow = sheet.createRow(rowOffset.getAndIncrement());
		String message = getValidationSummaryMessage(errors, warnings);
		Cell messageCell = messageRow.createCell(0);
		XSSFRichTextString textString = new XSSFRichTextString(message);
		messageCell.setCellValue(textString);
		messageCell.setCellStyle(cellStyle);

		rowOffset.getAndIncrement();
		rowOffset.getAndIncrement();

        addValidationFailures(testResult.assertionsFailed(), true, rowOffset, sheet, cellStyle);
        addValidationFailures(testResult.assertionsWarning(), false, rowOffset, sheet, cellStyle);

		// Example:
		// Report Timestamp: Feb 7, 2024, 5:50:04 PM (UTC), Identifier: 1707327746182
		Row metadataRow = sheet.createRow(rowOffset.getAndIncrement());
		metadataRow.createCell(0).setCellValue(String.format("Report Timestamp: %s (UTC), Identifier: %s",
				validationReport.rvfValidationResult().startTime(),
				validationReport.rvfValidationResult().validationConfig().runId()));

		resizeColumns(sheet);

		return workbook;
	}

	private static void addValidationFailures(List<ValidationReport.Assertion> assertions, boolean error,
											  AtomicInteger rowOffset, Sheet sheet, CellStyle cellStyle) {

		for (ValidationReport.Assertion assertion : assertions) {
			Row ruleRow = sheet.createRow(rowOffset.getAndIncrement());

			Cell cell = ruleRow.createCell(0);
			String assertionText = assertion.assertionText();
			int failureCount = assertion.failureCount();
			boolean single = failureCount == 1;
			String assertionMessage = String.format("%s: there %s %s %s of the following rule.\n" +
					"\"%s\"",
					error ? "Error" : "Warning",
					single ? "is" : "are",
					failureCount,
					single ? "failure" : "failures",
					assertionText);
			XSSFRichTextString ruleText = new XSSFRichTextString(assertionMessage);
			ruleText.applyFont(BOLD_FONT);
			cell.setCellValue(ruleText);
			cell.setCellStyle(cellStyle);

			Row failureRow = ruleRow;
			for (ValidationReport.AssertionIssue failureInstance : CollectionUtils.orEmpty(assertion.firstNInstances())) {
				failureRow.createCell(1).setCellValue(failureInstance.conceptId());

				StringBuilder detailMessage = new StringBuilder();
				String detail = failureInstance.detail();
				// Only add detail if it's different to the overall assertion text
				if (detail != null && !detail.equals(assertionText)) {
					detailMessage.append(detail);
				}

				if (failureInstance.conceptFsn() != null) {
					if (!detailMessage.isEmpty()) {
						detailMessage.append(NEW_LINE);
					}
					detailMessage.append(failureInstance.conceptFsn());
				}

				if (!detailMessage.isEmpty()) {
					failureRow.createCell(2).setCellValue(detailMessage.toString());
				}

				failureRow = sheet.createRow(rowOffset.getAndIncrement());// may be left blank if no failures left
			}
			rowOffset.getAndIncrement();
		}
	}

	private static String getValidationSummaryMessage(int errors, int warnings) {
		String message;
		if (errors > 0 || warnings > 0) {
			message = """
					The automatic validation process has found some content issues. These must be fixed before the Edition can be released.

					Some types of content issue trigger multiple validation rules, so fixing one issue many clear other rule failures for the same concept.""";
		} else {
			message = "The automatic validation process ran successfully and no content issues were found. " +
					"Please also carefully review the content manually before release.";
		}
		return message;
	}

	private static Map<String, List<String>> getDescriptionsPerLangRefset(List<Description> descriptions, List<String> langRefsetIds) {
		Map<String, List<String>> descriptionsPerLangRefset = new HashMap<>();
		for (String langRefsetId : langRefsetIds) {
			descriptionsPerLangRefset.put(langRefsetId, new ArrayList<>());
			// Find PTs
			for (Description description : descriptions) {
				if (description.isActive() && description.getType() == Description.Type.SYNONYM
						&& description.getAcceptabilityMap().get(langRefsetId) == Description.Acceptability.PREFERRED) {
					descriptionsPerLangRefset.get(langRefsetId).add(description.getTerm());
				}
			}
			// Find synonyms
			for (Description description : descriptions) {
				if (description.isActive() && description.getType() == Description.Type.SYNONYM
						&& description.getAcceptabilityMap().get(langRefsetId) == Description.Acceptability.ACCEPTABLE) {
					descriptionsPerLangRefset.get(langRefsetId).add(description.getTerm());
				}
			}
		}
		return descriptionsPerLangRefset;
	}

	private static CellStyle getCellStyle(Workbook workbook) {
		CellStyle cellStyle = workbook.createCellStyle();
		cellStyle.setDataFormat((short) BuiltinFormats.getBuiltinFormat("@"));
		cellStyle.setWrapText(true);// This does not actually cause text to be wrapped, but it's nice to have set anyway.
		cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
		return cellStyle;
	}

	private static void resizeColumns(Sheet sheet) {
		List<Row> rows = IntStream.range(0, sheet.getLastRowNum()).mapToObj(sheet::getRow).toList();
		resizeColumns(sheet, rows);
	}

	private static void resizeColumns(Sheet sheet, List<Row> rows) {
		for (int col = 0; col < 50; col++) {
			sheet.autoSizeColumn(col);
			int columnWidth = sheet.getColumnWidth(col);
			if (columnWidth > 15_000) {
				sheet.setColumnWidth(col, 15_000);
				// Adjust the height of cells with long strings.
				// The API does not seem capable of doing this automatically on a way that works on all platforms.
				for (Row dataRow : rows) {
					if (dataRow == null) continue;
					Cell cell = dataRow.getCell(col);
					if (cell != null) {
						RichTextString richStringCellValue = cell.getRichStringCellValue();
						String string = richStringCellValue.getString();
						int lines = (string.length() / 65) + 1;
						int newLines = string.split("\n").length - 1;
						dataRow.setHeight((short) (dataRow.getHeight() * (lines + newLines)));
					}
				}
			}
		}
	}

	public <T extends ComponentIntent> List<T> readComponentSpreadsheet(InputStream spreadsheetStream, List<SheetHeader> expectedHeader,
			SheetRowToComponentIntentExtractor<T> componentIntentExtractor, long expectedContentHeadTimestamp) throws ServiceException {

		int rowNumber = 0;
		try {
			List<T> components = new ArrayList<>();
			try (Workbook workbook = new XSSFWorkbook(spreadsheetStream)) {
				Sheet sheet = workbook.getSheetAt(0);
				HeaderConfiguration headerConfiguration = null;
				for (Row cells : sheet) {
					rowNumber++;
					if (headerConfiguration == null) {
						verifySheetMatchesLatestCommit(cells, expectedContentHeadTimestamp);
						headerConfiguration = getHeaderConfiguration(cells, expectedHeader);
					} else {
						T componentIntent = componentIntentExtractor.extract(cells, rowNumber, headerConfiguration);
						if (componentIntent != null) {
							components.add(componentIntent);
						}
					}
				}
				return components;
			}
		} catch (IOException e) {
			throw new ServiceException(String.format("Failed to read row %s of input file, %s", rowNumber, e.getMessage()));
		}
	}

	private void verifySheetMatchesLatestCommit(Row headerCells, long expectedContentHeadTimestamp) throws ServiceExceptionWithStatusCode {
		boolean timestampMatches = false;
		for (Cell cell : headerCells) {
			String stringCellValue = cell.getStringCellValue();
			if (stringCellValue != null && stringCellValue.startsWith(SIMPLEX_BRANCH_TIMESTAMP_PREFIX)) {
				String timestamp = stringCellValue.substring(SIMPLEX_BRANCH_TIMESTAMP_PREFIX.length());
				if (timestamp.matches("^\\d+$")) {
					long actualTimestamp = Long.parseLong(timestamp);
					if (expectedContentHeadTimestamp == actualTimestamp) {
						timestampMatches = true;
						break;
					}
				}
			}
		}
		if (!timestampMatches) {
			throw new ServiceExceptionWithStatusCode(
					"The uploaded spreadsheet is not up to date with the latest changes in the Edition. " +
							"Please download the latest spreadsheet from Simplex, add changes to that and upload.", HttpStatus.CONFLICT);
		}
	}

	/**
	 * Finds all required headers and returns a HeaderConfiguration with the found headers and their column index.
	 * @throws ServiceException if any required headers are not found.
	 */
	private HeaderConfiguration getHeaderConfiguration(Row actualHeaderRow, List<SheetHeader> expectedHeaders) throws ServiceException {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		Map<String, SheetHeader> expectedHeadersRemaining =
				expectedHeaders.stream().collect(Collectors.toMap((header) -> header.getName().toLowerCase().trim(), Function.identity()));

		for (int i = 0; i < 50; i++) {
			Cell actualHeaderCell = actualHeaderRow.getCell(i);
			if (actualHeaderCell != null) {
				String lowercaseHeaderName = actualHeaderCell.getStringCellValue().toLowerCase().trim();
				lowercaseHeaderName = lowercaseHeaderName.split("\n")[0];
				lowercaseHeaderName = stripBadCharacters(lowercaseHeaderName);
				SheetHeader matchingExpectedHeader = expectedHeadersRemaining.get(lowercaseHeaderName);
				if (matchingExpectedHeader != null) {
					expectedHeadersRemaining.remove(lowercaseHeaderName);
					headerConfiguration.addHeader(matchingExpectedHeader, i);
				}
			}
		}

		List<String> mandatoryExpectedHeaderNamesNotFound = expectedHeadersRemaining.values().stream()
				.filter(header -> !header.isOptional())
				.map(SheetHeader::getName)
				.toList();
		if (!mandatoryExpectedHeaderNamesNotFound.isEmpty()) {
			throw new ServiceException(String.format("Uploaded spreadsheet has mandatory columns missing. These are: %s. " +
							"Please add the missing columns with these headings and fill in the row values then try uploading again.",
					mandatoryExpectedHeaderNamesNotFound));
		}

		return headerConfiguration;
	}

	/**
	 * Strip invalid hidden characters found in the headers of spreadsheets downloaded from Snap2SNOMED.
	 */
	private String stripBadCharacters(String lowercaseHeaderName) {
		List<Byte> goodCharacters = new ArrayList<>();
		lowercaseHeaderName = lowercaseHeaderName.replace("\r", "");
		for (byte aByte : lowercaseHeaderName.getBytes(StandardCharsets.UTF_8)) {
			if (aByte > 0) {
				goodCharacters.add(aByte);
			}
		}
		byte[] chars = new byte[goodCharacters.size()];
		for (int i = 0; i < goodCharacters.size(); i++) {
			chars[i] = goodCharacters.get(i);
		}
		return new String(chars);
	}

	/**
	 * Reads a SNOMED CT concept id from the spreadsheet cell that may be formatted as either a string or number.
	 * Detects if the value has been corrupted by bad spreadsheet formatting and automatically fixes the SCTID by reconstructing the segment and check digit.
	 * @throws ServiceException if the identifier is corrupted beyond repair. This is not expected to happen for SCTIDs.
	 */
	public static String readSnomedConcept(Row cells, int column, int row) throws ServiceException {
		Cell cell = cells.getCell(column);

		if (cell == null) {
			return null;
		}
		String cellValue = null;
		CellType cellType = cell.getCellType();
		if (cellType == CellType.STRING) {
			cellValue = cell.getStringCellValue();
			if (cellValue != null && cellValue.contains("|")) {
				cellValue = cellValue.substring(cellValue.indexOf("|")).trim();
			}
		} else if (cellType == CellType.NUMERIC) {
			String rawValue = ((XSSFCell) cell).getRawValue();
			if (rawValue.contains("E")) {
				cellValue = fixConceptCode(rawValue, column, row);
			} else {
				cellValue = Long.toString((long) cell.getNumericCellValue());
			}
		}

		return cellValue;
	}

	protected static String fixConceptCode(String rawValue, int column, int row) throws ServiceException {
		String cellValue;
		Pattern pattern = Pattern.compile("([0-9.]+)E(\\+?)([0-9]+)");
		Matcher matcher = pattern.matcher(rawValue);
		if (matcher.matches()) {
			// Replace segment and checksum. This method works in all tested cases up to max permitted length of 16 digits.
			String part = matcher.group(1).replace(".", "");
			if (matcher.group(2).isEmpty()) {
				cellValue = part;
			} else {
				part = part + "10";
				char checkSum = VerhoeffCheck.calculateChecksum(part, false);
				cellValue = part + checkSum;
			}
		} else {
			throw new ServiceException(String.format("Unable to fix SNOMED CT concept id in column %s, row %s, the number is corrupted. Value '%s'.", column + 1, row, rawValue));
		}
		return cellValue;
	}

	/**
	 * Reads a generic code from the spreadsheet cell that may be formatted as either a string or number.
	 * If formatted as a number and above a certain length it is likely that the number will become corrupted and be
	 * returned containing the 'E' character. In this instance an exception will be thrown.
	 * @throws ServiceException If a code is formatted as a number and is too long to be read from the sheet correctly.
	 */
	protected static String readGenericCode(Row cells, Integer column, int row) throws ServiceException {
		if (column == null) {
			return null;
		}
		Cell cell = cells.getCell(column);
		if (cell == null) {
			return null;
		}
		String cellValue = null;
		CellType cellType = cell.getCellType();
		if (cellType == CellType.STRING) {
			cellValue = cell.getStringCellValue();
			if (cellValue != null && cellValue.contains("|")) {
				cellValue = cellValue.substring(cellValue.indexOf("|")).trim();
			}
		} else if (cellType == CellType.NUMERIC || cellType == CellType.FORMULA) {
			String rawValue = ((XSSFCell) cell).getRawValue();
			if (rawValue.contains("E")) {
				throw new ServiceException(String.format("Unable to read code in column %s, row %s. An 'E' character has been added because the raw value of the number is too " +
						"long to be stored in a spreadsheet. Try formatting column %s using the string type instead and make sure to correct all numbers containing 'E'.",
						column + 1, row + 1, column + 1));
			}

			long value = (long) cell.getNumericCellValue();
			cellValue = Long.toString(value);
		} else if (cellType == CellType.BOOLEAN) {
			cellValue = cell.getBooleanCellValue() ? "1" : "0";
		}

		return cellValue;
	}
}
