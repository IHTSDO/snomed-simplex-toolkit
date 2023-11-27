package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.domain.ComponentIntent;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.spreadsheet.HeaderConfiguration;
import com.snomed.simplextoolkit.service.spreadsheet.SheetHeader;
import com.snomed.simplextoolkit.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.snomed.simplextoolkit.client.domain.Concepts.IS_A;

@Service
public class SpreadsheetService {

	public static final XSSFFont BOLD_FONT = new XSSFFont();
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

	public Workbook createConceptSpreadsheet(List<SheetHeader> headers, List<Concept> concepts, List<ConceptMini> langRefsets) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet();

		// Format all value cells as text.
		// To prevent them being automatically formatted as number because that can lead to formatting / rounding issues.
		CellStyle cellStyle = getCellStyle(workbook);

		int rowOffset = 0;
		Row headerRow = sheet.createRow(rowOffset++);
		headerRow.setHeight((short) (headerRow.getHeight() * 4));
		int columnOffset = 0;
		for (SheetHeader header : headers) {
			Cell cell = headerRow.createCell(columnOffset);
			XSSFRichTextString textString = new XSSFRichTextString();
			textString.append(header.getName(), BOLD_FONT);
			if (header.getSubtitle() != null) {
				textString.append("\r\n");
				textString.append(header.getSubtitle());
			}
			cell.setCellValue(textString);
			CellStyle cellStyle1 = cell.getCellStyle();
			cellStyle1.setWrapText(true);
			cellStyle1.setVerticalAlignment(VerticalAlignment.CENTER);
			cell.setCellStyle(cellStyle1);
			columnOffset++;
		}
		List<Row> dataRows = new ArrayList<>();
		for (Concept concept : concepts) {
			columnOffset = 0;
			Row row = sheet.createRow(rowOffset++);
			// Parent Concept Identifier | Parent Concept Term | Concept Identifier | Active | Terms in English, US dialect | [0.*] Terms in X
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

			Cell cell = row.createCell(columnOffset++);
			cell.setCellStyle(cellStyle);
			cell.setCellValue(parentId);

			cell = row.createCell(columnOffset++);
			cell.setCellStyle(cellStyle);
			cell.setCellValue(parentFSN);

			cell = row.createCell(columnOffset++);
			cell.setCellStyle(cellStyle);
			cell.setCellValue(concept.getConceptId());

			cell = row.createCell(columnOffset++);
			cell.setCellStyle(cellStyle);
			cell.setCellValue(concept.isActive() ? "" : "false");

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
					termColumnOffset++;
				}
			}

			dataRows.add(row);
		}

		resizeColumns(sheet, dataRows);

		return workbook;
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

	private static void resizeColumns(Sheet sheet, List<Row> dataRows) {
		for (int col = 0; col < 50; col++) {
			sheet.autoSizeColumn(col);
			int columnWidth = sheet.getColumnWidth(col);
			if (columnWidth > 15_000) {
				sheet.setColumnWidth(col, 15_000);
				// Adjust the height of cells with long strings.
				// The API does not seem capable of doing this automatically on a way that works on all platforms.
				for (Row dataRow : dataRows) {
					Cell cell = dataRow.getCell(col);
					if (cell != null) {
						RichTextString richStringCellValue = cell.getRichStringCellValue();
						int lines = (richStringCellValue.getString().length() / 65) + 1;
						dataRow.setHeight((short) (dataRow.getHeight() * lines));
					}
				}
			}
		}
	}

	public <T extends ComponentIntent> List<T> readComponentSpreadsheet(InputStream spreadsheetStream,
			List<SheetHeader> expectedHeader, SheetRowToComponentIntentExtractor<T> componentIntentExtractor) throws ServiceException {

		int rowNumber = 0;
		try {
			List<T> components = new ArrayList<>();
			Workbook workbook = new XSSFWorkbook(spreadsheetStream);
			Sheet sheet = workbook.getSheetAt(0);
			HeaderConfiguration headerConfiguration = null;
			for (Row cells : sheet) {
				rowNumber++;
				if (headerConfiguration == null) {
					headerConfiguration = getHeaderConfiguration(cells, expectedHeader);
				} else {
					T componentIntent = componentIntentExtractor.extract(cells, rowNumber, headerConfiguration);
					if (componentIntent != null) {
						components.add(componentIntent);
					}
				}
			}
			return components;
		} catch (IOException e) {
			throw new ServiceException(String.format("Failed to read row %s of input file, %s", rowNumber, e.getMessage()));
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
				Pattern pattern = Pattern.compile("([0-9.]+)E\\+([0-9]+)");
				Matcher matcher = pattern.matcher(rawValue);
				if (matcher.matches()) {
					// Replace segment and checksum. This method works in all tested cases up to max permitted length of 16 digits.
					String part = matcher.group(1).replace(".", "");
					part = part + "10";
					char checkSum = VerhoeffCheck.calculateChecksum(part, false);
					cellValue = part + checkSum;
				} else {
					throw new ServiceException(String.format("Unable to fix SNOMED CT concept id in column %s, row %s, the number is corrupted.", column + 1, row + 1));
				}
			} else {
				cellValue = Long.toString((long) cell.getNumericCellValue());
			}
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
