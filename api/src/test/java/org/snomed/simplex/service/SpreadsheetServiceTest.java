package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.snomed.simplex.TestConfig;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.DescriptionMini;
import org.snomed.simplex.domain.ConceptIntent;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
class SpreadsheetServiceTest {

	@Autowired
	private SpreadsheetService spreadsheetService;

	@Autowired
	private CustomConceptService customConceptService;
	private List<SheetHeader> basicSheetHeaders;
	private SheetRowToComponentIntentExtractor<ConceptIntent> basicComponentExtractor;
	private long correctTimestamp;

	@BeforeEach
	void setUp() {
		basicSheetHeaders = customConceptService.getInputSheetHeaders(Collections.emptyList());
		basicComponentExtractor = customConceptService.getInputSheetComponentExtractor(Collections.emptyList());
		correctTimestamp = 1727964880378L;
	}

	@Test
	void fixConceptCode() throws ServiceException {
		assertEquals("165477451000003105", SpreadsheetService.fixConceptCode("1.65477451000003E+017", 1, 1));
		assertEquals("195967001", SpreadsheetService.fixConceptCode("1.95967001E8", 1, 1));
	}

	@Test
	void testSpreadsheetUpdateCheck() {
		// Test with correct timestamp
		try {
			InputStream inputStream = getClass().getResourceAsStream("/test-spreadsheets/conceptsSpreadsheet.xlsx");
			spreadsheetService.readComponentSpreadsheet(inputStream, basicSheetHeaders, basicComponentExtractor, correctTimestamp);
		} catch (ServiceException e) {
			fail("Timestamp check failed, it should have passed.");
		}

		// Test with incorrect timestamp
		try {
			long incorrectTimestamp = 1727964880000L;
			InputStream inputStream = getClass().getResourceAsStream("/test-spreadsheets/conceptsSpreadsheet.xlsx");
			spreadsheetService.readComponentSpreadsheet(inputStream, basicSheetHeaders, basicComponentExtractor, incorrectTimestamp);
			fail("Timestamp check passed, it should have failed.");
		} catch (ServiceException e) {
			// Pass
		}
	}

	@Test
	void testSpreadsheetBlankTerm() {
		try {
			InputStream inputStream = getClass().getResourceAsStream("/test-spreadsheets/conceptsSpreadsheet-blank-term.xlsx");
			spreadsheetService.readComponentSpreadsheet(inputStream, basicSheetHeaders, basicComponentExtractor, correctTimestamp);
			fail("Upload passed, it should have failed.");
		} catch (ServiceException e) {
			assertTrue(e instanceof ServiceExceptionWithStatusCode withStatusCode && withStatusCode.getJobStatus() == JobStatus.USER_CONTENT_ERROR);
		}
	}

	@Test
	void testReadSheet() throws ServiceException {
		InputStream inputStream = getClass().getResourceAsStream("/test-spreadsheets/conceptsSpreadsheet.xlsx");

		String langRefsetA = "971232511000003100";
		String langRefsetB = "214916581000003106";
		List<ConceptMini> langRefsets = List.of(
				new ConceptMini(langRefsetA, new DescriptionMini("Some language reference set", "en")),
				new ConceptMini(langRefsetB, new DescriptionMini("My translation", "en"))
		);
		List<SheetHeader> inputSheetHeaders = customConceptService.getInputSheetHeaders(langRefsets);
		assertEquals(7, inputSheetHeaders.size());
		assertEquals("Terms in Some language reference set (971232511000003100)", inputSheetHeaders.get(5).getName());
		assertEquals("Terms in My translation (214916581000003106)", inputSheetHeaders.get(6).getName());

		SheetRowToComponentIntentExtractor<ConceptIntent> inputSheetComponentExtractor =
				customConceptService.getInputSheetComponentExtractor(List.of(langRefsetA, langRefsetB));
		List<ConceptIntent> sheetConcepts = spreadsheetService.readComponentSpreadsheet(inputStream, inputSheetHeaders, inputSheetComponentExtractor, 1727964880378L);
		assertEquals(11, sheetConcepts.size());

		ConceptIntent conceptIntent1 = sheetConcepts.get(1);
		assertNotNull(conceptIntent1);
		Map<String, List<String>> langRefsetTerms = conceptIntent1.getLangRefsetTerms();
		assertEquals(1, langRefsetTerms.size());
		assertEquals(1, langRefsetTerms.get(Concepts.US_LANG_REFSET).size());
		assertEquals("Test Subset", langRefsetTerms.get(Concepts.US_LANG_REFSET).get(0));

		ConceptIntent conceptIntent2 = sheetConcepts.get(2);
		assertNotNull(conceptIntent2);
		langRefsetTerms = conceptIntent2.getLangRefsetTerms();
		assertEquals(3, langRefsetTerms.size());

		assertEquals(1, langRefsetTerms.get(Concepts.US_LANG_REFSET).size());
		assertEquals("ATC to SNOMED CT Substance Map", langRefsetTerms.get(Concepts.US_LANG_REFSET).get(0));

		assertEquals(1, langRefsetTerms.get(langRefsetA).size());
		assertEquals("ATC to SNOMED CT Substance Map", langRefsetTerms.get(langRefsetA).get(0));

		assertEquals(1, langRefsetTerms.get(langRefsetB).size());
		assertEquals("ATC to SNOMED CT Substance Map", langRefsetTerms.get(langRefsetB).get(0));

		ConceptIntent conceptIntent3 = sheetConcepts.get(3);
		assertNotNull(conceptIntent3);
		langRefsetTerms = conceptIntent3.getLangRefsetTerms();
		assertEquals(3, langRefsetTerms.size());

		assertEquals(1, langRefsetTerms.get(Concepts.US_LANG_REFSET).size());
		assertEquals("My translation", langRefsetTerms.get(Concepts.US_LANG_REFSET).get(0));

		assertEquals(1, langRefsetTerms.get(langRefsetA).size());
		assertEquals("My translation a", langRefsetTerms.get(langRefsetA).get(0));

		assertEquals(2, langRefsetTerms.get(langRefsetB).size());
		assertEquals("My translation b", langRefsetTerms.get(langRefsetB).get(0));
		assertEquals("My translation bb", langRefsetTerms.get(langRefsetB).get(1));
	}
}
