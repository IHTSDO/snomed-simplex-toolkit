package org.snomed.simplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.domain.ConceptIntent;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CustomConceptServiceTest {

	@Mock
	private SnowstormClient mockSnowstormClient;

	@Autowired
	private CustomConceptService customConceptService;

	@Autowired
	private ObjectMapper objectMapper;

	@Captor
	private ArgumentCaptor<List<Concept>> conceptListCaptor;

	private AutoCloseable closeable;

	@BeforeEach
	public void open() {
		closeable = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	public void release() throws Exception {
		closeable.close();
	}

	@Test
	void createUpdateConcepts() throws ServiceException, IOException {
		String dummyModule = "101000003010";
		String patientFriendlyTerm = "121000003010";
		CodeSystem codeSystem = new CodeSystem("test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule(dummyModule);
		List<ConceptIntent> conceptIntents = new ArrayList<>();
		char nonBreakingSpaceCharacter = (char) 160;
		conceptIntents.add(new ConceptIntent("  429926006  ", 1)
				.addTerm("CT of left middle ear", Concepts.US_LANG_REFSET)
				.addTerm("Scan of left middle ear using CT machine", patientFriendlyTerm)
				.addTerm("CT of left middle" + nonBreakingSpaceCharacter + "ear", patientFriendlyTerm)
		);

		List<Concept> parentConcepts = new ArrayList<>();
		parentConcepts.add(objectMapper.readValue(getClass().getResourceAsStream("/dummy-concepts/429926006.json"), Concept.class));
		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem))).thenReturn(parentConcepts);
		// TODO Add languages - store in branch metadata? - get from the user up front.
		ChangeSummary changeSummary = customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET, patientFriendlyTerm),
				new ContentJob("", "", null), mockSnowstormClient);

		Mockito.verify(mockSnowstormClient).createUpdateBrowserFormatConcepts(conceptListCaptor.capture(), Mockito.eq(codeSystem));
		List<Concept> conceptsSaved = conceptListCaptor.getValue();
		assertEquals(1, conceptsSaved.size());
		Concept concept1 = conceptsSaved.get(0);
		assertEquals(dummyModule, concept1.getModuleId());
		assertEquals("PRIMITIVE", concept1.getDefinitionStatus());
		List<Description> descriptions = concept1.getDescriptions();
		for (Description description : descriptions) {
			System.out.println(description);
		}
		assertEquals(3, descriptions.size());
		List<String> expectedDescriptions = List.of(
				"Description{lang='en', term='CT of left middle ear (procedure)', caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', " +
						"acceptabilityMap={900000000000509007=PREFERRED}}",
				"Description{lang='en', term='CT of left middle ear', caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', " +
						"acceptabilityMap={900000000000509007=PREFERRED, 121000003010=ACCEPTABLE}}",
				"Description{lang='en', term='Scan of left middle ear using CT machine', caseSignificance='ENTIRE_TERM_CASE_SENSITIVE', " +
						"acceptabilityMap={121000003010=PREFERRED}}"
		);
		assertTrue(expectedDescriptions.contains(descriptions.get(0).toString()));
		assertTrue(expectedDescriptions.contains(descriptions.get(1).toString()));
		assertTrue(expectedDescriptions.contains(descriptions.get(2).toString()));

		assertEquals(1, changeSummary.getAdded());
		assertEquals(0, changeSummary.getUpdated());
		assertEquals(0, changeSummary.getRemoved());
		assertEquals(1, changeSummary.getNewTotal());
	}
}
