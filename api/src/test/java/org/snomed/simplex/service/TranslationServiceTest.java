package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.simplex.TestConfig;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.client.domain.DummyProgressMonitor;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.snomed.simplex.client.domain.Description.CaseSignificance.*;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
class TranslationServiceTest {

	@Autowired
	private TranslationService service;

	@MockBean
	private SnowstormClientFactory snowstormClientFactory;

	@Captor
	private ArgumentCaptor<List<Concept>> conceptsSentToUpdate;

	private final CodeSystem testCodeSystem = new CodeSystem("SNOMEDCT-TEST", "", "MAIN/SNOMEDCT-TEST");

	private SnowstormClient mockSnowstormClient;

	private final String testLangRefset = "123000";

	@BeforeEach
	void setup() throws ServiceException {
		mockSnowstormClient = Mockito.mock(SnowstormClient.class);
		Mockito.when(snowstormClientFactory.getClient()).thenReturn(mockSnowstormClient);
		testCodeSystem.setTranslationLanguages(Map.of(testLangRefset, "vi"));
	}

	@Test
	void guessCaseSignificance() {
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, service.guessCaseSignificance("SNOMED CT core module (core metadata concept)", true, null));
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, service.guessCaseSignificance("sinh thiết chọc hút bằng kim nhỏ nang giả tụy có hướng dẫn CT", false, null));
		assertEquals(CASE_INSENSITIVE, service.guessCaseSignificance("sinh thiết chọc hút bằng kim nhỏ nang giả tụy có hướng dẫn", false, null));
		assertEquals(CASE_INSENSITIVE, service.guessCaseSignificance("Clinical finding (finding)", true, null));
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, service.guessCaseSignificance("Clinical finding (finding)", false, null));
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, service.guessCaseSignificance("Neisseria meningitidis gruppe Z", true, null));

		List<Description> otherDescriptions = List.of(
				new Description(Description.Type.SYNONYM, "en", "Smith fracture", ENTIRE_TERM_CASE_SENSITIVE),
				new Description(Description.Type.SYNONYM, "en", "Carbapenem resistant Escherichia coli", INITIAL_CHARACTER_CASE_INSENSITIVE),
				new Description(Description.Type.SYNONYM, "en", "Something fracture", CASE_INSENSITIVE)
		);
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, service.guessCaseSignificance("Smith thing", true, otherDescriptions),
				"Case sensitive if first word matches another description that is case sensitive.");
		assertEquals(CASE_INSENSITIVE, service.guessCaseSignificance("Something thing", true, otherDescriptions),
				"Case insensitive because first word does not match another description that is case sensitive");
		assertEquals(INITIAL_CHARACTER_CASE_INSENSITIVE, service.guessCaseSignificance("Carbapenem resistant Escherichia", true, otherDescriptions),
				"Initial character case sensitive if first word matches another description that has this case sensitivity.");
	}

	@Test
	void testBlankHeader() throws ServiceException {
		SnowstormClient client = snowstormClientFactory.getClient();
		try {
			service.uploadTranslationAsWeblateCSV(testLangRefset, testCodeSystem, getClass().getResourceAsStream("/test-translation-blank.txt"), false,
					client, new DummyProgressMonitor());
			fail();
		} catch (ServiceException e) {
			assertEquals("Unrecognised CSV header ''", e.getMessage());
		}
	}

	@Test
	void testAdd() throws ServiceException {
		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.any(), Mockito.any())).thenReturn(
				List.of(new Concept("").setConceptId("880529761000119102"),
						new Concept("").setConceptId("740215071000132100"),
						new Concept("").setConceptId("674814021000119106")));
		Mockito.doNothing().when(mockSnowstormClient).createUpdateBrowserFormatConcepts(conceptsSentToUpdate.capture(), Mockito.any());

		service.uploadTranslationAsWeblateCSV(testLangRefset, testCodeSystem, getClass().getResourceAsStream("/test-translation-1.txt"), false,
				snowstormClientFactory.getClient(), new DummyProgressMonitor());

		List<Concept> updatedConcepts = conceptsSentToUpdate.getValue();
		assertEquals(3, updatedConcepts.size());
		Concept concept = updatedConcepts.get(0);
		assertEquals("880529761000119102", concept.getConceptId());
		assertEquals("[Description{lang='vi', term='nhiễm trùng đường hô hấp dưới do SARS-CoV-2', " +
				"caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', acceptabilityMap={123000=PREFERRED}}]", Arrays.toString(concept.getDescriptions().toArray()));
		concept = updatedConcepts.get(1);
		assertEquals("740215071000132100", concept.getConceptId());
		assertEquals("[Description{lang='vi', term='những thay đổi của liệu pháp miễn dịch trong bàng quang sau tiêm BCG', " +
				"caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', acceptabilityMap={123000=PREFERRED}}]", Arrays.toString(concept.getDescriptions().toArray()));
		concept = updatedConcepts.get(2);
		assertEquals("674814021000119106", concept.getConceptId());
		assertEquals("[Description{lang='vi', term='hội chứng suy hô hấp cấp tính gây ra bởi coronavirus 2 gây hội chứng hô hấp cấp tính nặng', " +
				"caseSignificance='CASE_INSENSITIVE', acceptabilityMap={123000=PREFERRED}}]", Arrays.toString(concept.getDescriptions().toArray()));
	}

	@Test
	void testUploadRefsetAndTranslationToolWrongLanguage() throws IOException {
		try {
			File exportFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/refset-translation-tool-example-export");
			service.uploadTranslationAsRefsetToolArchive(testLangRefset, testCodeSystem, new FileInputStream(exportFile), true, new DummyProgressMonitor());
			fail();
		} catch (ServiceException e) {
			assertEquals("3 of the uploaded terms have an incorrect language. Set language code to 'vi' for all terms and try again.", e.getMessage());
		}
	}
}
