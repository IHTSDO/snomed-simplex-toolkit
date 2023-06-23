package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.Concept;
import com.snomed.simplextoolkit.domain.CodeSystem;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TranslationServiceTest {

	@Autowired
	private TranslationService service;

	@MockBean
	private SnowstormClientFactory snowstormClientFactory;

	@Captor
	private ArgumentCaptor<List<Concept>> conceptsSentToUpdate;

	private CodeSystem testCodeSystem = new CodeSystem("SNOMEDCT-TEST", "", "MAIN/SNOMEDCT-TEST");

	private SnowstormClient mockSnowstormClient;

	@BeforeEach
	void setup() throws ServiceException {
		mockSnowstormClient = Mockito.mock(SnowstormClient.class);
		Mockito.when(snowstormClientFactory.getClient()).thenReturn(mockSnowstormClient);
	}

	@Test
	void guessCaseSignificance() {
		assertEquals("ENTIRE_TERM_CASE_SENSITIVE", service.guessCaseSignificance("SNOMED CT core module (core metadata concept)", true));
		assertEquals("ENTIRE_TERM_CASE_SENSITIVE", service.guessCaseSignificance("sinh thiết chọc hút bằng kim nhỏ nang giả tụy có hướng dẫn CT", false));
		assertEquals("CASE_INSENSITIVE", service.guessCaseSignificance("sinh thiết chọc hút bằng kim nhỏ nang giả tụy có hướng dẫn", false));
		assertEquals("CASE_INSENSITIVE", service.guessCaseSignificance("Clinical finding (finding)", true));
		assertEquals("ENTIRE_TERM_CASE_SENSITIVE", service.guessCaseSignificance("Clinical finding (finding)", false));
	}

	@Test
	void testBlankHeader() {
		try {
			service.uploadTranslationAsCSV("", "fr", testCodeSystem, getClass().getResourceAsStream("/test-translation-blank.txt"), false, false, snowstormClientFactory.getClient());
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
		Mockito.doNothing().when(mockSnowstormClient).updateBrowserFormatConcepts(conceptsSentToUpdate.capture(), Mockito.any());

		service.uploadTranslationAsCSV("", "vi", testCodeSystem, getClass().getResourceAsStream("/test-translation-1.txt"), false, false, snowstormClientFactory.getClient());

		List<Concept> updatedConcepts = conceptsSentToUpdate.getValue();
		assertEquals(3, updatedConcepts.size());
		Concept concept = updatedConcepts.get(0);
		assertEquals("880529761000119102", concept.getConceptId());
		assertEquals("[Description{lang='vi', term='nhiễm trùng đường hô hấp dưới do SARS-CoV-2', " +
				"caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', acceptabilityMap={=PREFERRED}}]", Arrays.toString(concept.getDescriptions().toArray()));
		concept = updatedConcepts.get(1);
		assertEquals("740215071000132100", concept.getConceptId());
		assertEquals("[Description{lang='vi', term='những thay đổi của liệu pháp miễn dịch trong bàng quang sau tiêm BCG', " +
				"caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', acceptabilityMap={=PREFERRED}}]", Arrays.toString(concept.getDescriptions().toArray()));
		concept = updatedConcepts.get(2);
		assertEquals("674814021000119106", concept.getConceptId());
		assertEquals("[Description{lang='vi', term='hội chứng suy hô hấp cấp tính gây ra bởi coronavirus 2 gây hội chứng hô hấp cấp tính nặng', " +
				"caseSignificance='CASE_INSENSITIVE', acceptabilityMap={=PREFERRED}}]", Arrays.toString(concept.getDescriptions().toArray()));
	}

}
