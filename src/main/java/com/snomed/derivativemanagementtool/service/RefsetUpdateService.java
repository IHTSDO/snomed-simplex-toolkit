package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.domain.CodeSystem;
import com.snomed.derivativemanagementtool.domain.Product;
import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@Service
public class RefsetUpdateService {

	public static final String LINE_BREAK = "------------------------------------";

	@Autowired
	private CodeSystemConfigService configService;

	public void update(Product product, SnowstormClient snowstormClient) {
		System.out.println(LINE_BREAK);
		try {
			CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(product.getCodesystem());

			System.out.printf("Processing product '%s'.%n", product.getName());
			File productDir = product.getProductDir();

			// Read members from file
			List<String> inputMembers = readMembers(productDir);
			System.out.printf("Read %s members from input file.%n", inputMembers.size());
			System.out.println();

			// Read members from store
			String branchPath = codeSystem.getBranchPath();
			System.out.printf("Reading members from store...%n");
			List<RefsetMember> allStoredMembers = snowstormClient.loadAllRefsetMembers(branchPath);
			System.out.printf("Read %s members from store.%n", allStoredMembers.size());
			System.out.println();

			Map<String, List<RefsetMember>> storedMemberMap = new HashMap<>();
			for (RefsetMember storedMember : allStoredMembers) {
				storedMemberMap.computeIfAbsent(storedMember.getReferencedComponentId(), key -> new ArrayList<>()).add(storedMember);
			}

			// Members to create
			List<RefsetMember> membersToCreate = new ArrayList<>();
			List<RefsetMember> membersToUpdate = new ArrayList<>();
			List<RefsetMember> membersToKeep = new ArrayList<>();

			for (String inputMember : inputMembers) {
				// Lookup existing member(s) for component
				List<RefsetMember> storedMembers = storedMemberMap.getOrDefault(inputMember, Collections.emptyList());
				if (storedMembers.isEmpty()) {
					// None exist, create
					membersToCreate.add(new RefsetMember(product.getRefsetId(), product.getModule(), inputMember));
				} else {
					RefsetMember memberToKeep;
					if (storedMembers.size() == 1) {
						memberToKeep = storedMembers.get(0);
					} else {
						// Find best member
						storedMembers.sort(Comparator.comparing(RefsetMember::isReleased).thenComparing(RefsetMember::isActive));
						memberToKeep = storedMembers.get(0);
					}
					// Keep remaining member
					if (!memberToKeep.isActive()) {
						// Not active, update to make active
						memberToKeep.setActive(true);
						membersToUpdate.add(memberToKeep);
					} else {
						// Keep as-is
						membersToKeep.add(memberToKeep);
					}
				}
			}

			List<RefsetMember> membersToRemove = allStoredMembers.stream().filter(not(membersToKeep::contains)).collect(Collectors.toList());

			List<RefsetMember> membersToInactivate = membersToRemove.stream().filter(RefsetMember::isReleased).collect(Collectors.toList());
			List<RefsetMember> membersToDelete = membersToRemove.stream().filter(not(RefsetMember::isReleased)).collect(Collectors.toList());

			System.out.printf("%s members need to be created.%n", membersToCreate.size());
			System.out.printf("%s members need to be deleted.%n", membersToDelete.size());
			System.out.printf("%s members need to be inactivated.%n", membersToInactivate.size());

			// Assemble create / update / inactivate members
			List<RefsetMember> membersToUpdateCreate = new ArrayList<>(membersToCreate);
			membersToUpdateCreate.addAll(membersToUpdate);
			membersToInactivate.forEach(memberToInactivate -> memberToInactivate.setActive(false));
			membersToUpdateCreate.addAll(membersToInactivate);
			if (!membersToUpdateCreate.isEmpty()) {
				// Send all as batch
				System.out.print("Running bulk create/update... ");
				snowstormClient.createUpdateRefsetMembers(membersToUpdateCreate);
				System.out.println("done");
			}
			if (!membersToDelete.isEmpty()) {
				System.out.print("Running bulk delete... ");
				snowstormClient.deleteRefsetMembers(membersToDelete);
				System.out.println("done");
			}

			System.out.printf("Processing product '%s' complete.%n", product.getName());
		} catch (ServiceException e) {
			System.out.println();
			System.err.printf("Processing product '%s' failed.%n", product.getName());
			System.out.println(e.getMessage());
			System.out.println();
		}
		System.out.println(LINE_BREAK);
	}

	private List<String> readMembers(File productDir) throws ServiceException {
		List<String> inputMembers = new ArrayList<>();
		File[] fileList = productDir.listFiles();
		if (fileList != null) {
			Optional<File> excelFile = Arrays.stream(fileList).filter(file -> file.isFile() && file.getName().endsWith(".xlsx")).findFirst();
			if (excelFile.isPresent()) {
				System.out.printf("Reading member file %s%n", excelFile.get().getName());
				readSpreadsheetFile(excelFile.get(), inputMembers);
				return inputMembers;
			}

			Optional<File> txtFile = Arrays.stream(fileList).filter(file -> file.isFile() && file.getName().endsWith(".txt")).findFirst();
			if (txtFile.isPresent()) {
				System.out.printf("Reading member file %s%n", txtFile.get().getName());
				readTextFile(txtFile.get(), inputMembers);
				return inputMembers;
			}
		}
		throw new ServiceException("No refset members file found.");
	}

	private void readSpreadsheetFile(File spreadsheet, List<String> inputMembers) throws ServiceException {
		try {
			Workbook workbook = new XSSFWorkbook(spreadsheet);
			Sheet sheet = workbook.getSheetAt(0);
			boolean readingHeader = true;
			for (Row cells : sheet) {
				if (readingHeader) {
					Cell headerCell = cells.getCell(0);
					if (!"conceptId".equals(headerCell.getStringCellValue())) {
						throw new ServiceException(String.format("Unexpected first row of members file '%s'", spreadsheet.getAbsolutePath()));
					}
					readingHeader = false;
				} else {
					Cell cell = cells.getCell(0);
					if (cell != null) {
						String cellValue = cell.getStringCellValue();
						if (cellValue != null && !cellValue.isBlank()) {
							inputMembers.add(cellValue);
						}
					}
				}
			}
		} catch (IOException | InvalidFormatException e) {
			throw new ServiceException(String.format("Failed to read members file '%s', %s", spreadsheet.getAbsolutePath(), e.getMessage()));
		}
	}

	private void readTextFile(File membersFile, List<String> inputMembers) {
		try (BufferedReader reader = new BufferedReader(new FileReader(membersFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				inputMembers.add(line);
			}
		} catch (IOException e) {
			System.err.printf("Failed to read file %s%n.", membersFile.getAbsoluteFile());
			e.printStackTrace();
		}
	}

	public void downloadSimpleRefset(String refsetId, OutputStream outputStream) throws ServiceException, IOException {
		SnowstormClient snowstormClient = configService.getSnowstormClient();
		List<RefsetMember> members = snowstormClient.loadAllRefsetMembers(refsetId);
		sortSimpleRefset(members);

		Map<String, Function<RefsetMember, String>> simpleRefsetColumns = new LinkedHashMap<>();
		simpleRefsetColumns.put("conceptId", RefsetMember::getReferencedComponentId);

		Workbook workbook = createSpreadsheet(members, simpleRefsetColumns);
		workbook.write(outputStream);
	}

	private Workbook createSpreadsheet(List<RefsetMember> members, Map<String, Function<RefsetMember, String>> simpleRefsetColumns) {
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

	private void sortSimpleRefset(List<RefsetMember> members) {
		members.sort(Comparator.comparing(RefsetMember::getReferencedComponentId));
	}
}
