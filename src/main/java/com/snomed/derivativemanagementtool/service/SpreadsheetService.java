package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
						String cellValue = cell.getStringCellValue();
						if (cellValue != null && !cellValue.isBlank()) {
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
