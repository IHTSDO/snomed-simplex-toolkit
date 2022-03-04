package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpreadsheetService {

	public Workbook createSpreadsheet(List<RefsetMember> members, Map<String, Function<RefsetMember, String>> simpleRefsetColumns) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet();
		int rowOffset = 0;
		Row headerRow = sheet.createRow(rowOffset++);
		int columnOffset = 0;
		for (String columnName : simpleRefsetColumns.keySet()) {
			headerRow.createCell(columnOffset++).setCellValue(columnName);
		}
		for (RefsetMember member : members) {
			columnOffset = 0;
			Row row = sheet.createRow(rowOffset++);
			for (Map.Entry<String, Function<RefsetMember, String>> column : simpleRefsetColumns.entrySet()) {
				row.createCell(columnOffset++).setCellValue(column.getValue().apply(member));
			}
		}
		return workbook;
	}

	public List<String> readSimpleRefsetSpreadsheet(InputStream spreadsheetStream) throws ServiceException {
		try {
			List<String> members = new ArrayList<>();
			Workbook workbook = new XSSFWorkbook(spreadsheetStream);
			Sheet sheet = workbook.getSheetAt(0);
			boolean readingHeader = true;
			for (Row cells : sheet) {
				if (readingHeader) {
					Cell headerCell = cells.getCell(0);
					if (!"conceptId".equals(headerCell.getStringCellValue())) {
						throw new ServiceException("Unexpected first row of members spreadsheet. First cell should be 'conceptId'.");
					}
					readingHeader = false;
				} else {
					Cell cell = cells.getCell(0);
					if (cell != null) {
						CellType cellType = cell.getCellType();
						String cellValue = "";
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
									System.out.println("Converting raw value " + rawValue);
									String part = matcher.group(1).replace(".", "");
									part = part + "10";
									char checkSum = VerhoeffCheck.calculateChecksum(part, false);
									rawValue = part + checkSum;
									System.out.println("Raw value " + rawValue);
								}
							}

							long value = (long) cell.getNumericCellValue();
							cellValue = Long.toString(value);
//							System.out.println("Cell value " + cellValue);
						}

						if (cellValue != null && !cellValue.isBlank()) {
							// Fix checksum
							char checkDigit = VerhoeffCheck.calculateChecksum(cellValue, true);
							cellValue = cellValue.substring(0, cellValue.length() - 1) + checkDigit;
							members.add(cellValue);
						}
					}
				}
			}
			return members;
		} catch (IOException e) {
			throw new ServiceException(String.format("Failed to read members file, %s", e.getMessage()));
		}
	}
}
