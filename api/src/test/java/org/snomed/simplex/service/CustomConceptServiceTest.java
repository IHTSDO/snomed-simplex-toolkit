package org.snomed.simplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.domain.ConceptIntent;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;

@SpringBootTest
@ActiveProfiles("test")
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
		codeSystem.setTranslationLanguages(Map.of());
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
		ChangeSummary changeSummary = customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET, patientFriendlyTerm),
				new ContentJob(new CodeSystem(), "", null), mockSnowstormClient);

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
				"Description{lang='en', term='Scan of left middle ear using CT machine', caseSignificance='INITIAL_CHARACTER_CASE_INSENSITIVE', " +
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

	@Test
	void createUpdateConcepts_externalConceptIdWhenAllowed() throws ServiceException, IOException {
		String dummyModule = "101000003010";
		String externalConceptId = "123456789012345678";
		CodeSystem codeSystem = new CodeSystem("test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule(dummyModule);
		codeSystem.setTranslationLanguages(Map.of());
		codeSystem.setConceptsMaintainedExternally(true);

		ConceptIntent intent = new ConceptIntent("429926006", 1);
		intent.setConceptCode(externalConceptId);
		intent.addTerm("External concept term", Concepts.US_LANG_REFSET);
		List<ConceptIntent> conceptIntents = List.of(intent);

		Concept parentConcept = objectMapper.readValue(getClass().getResourceAsStream("/dummy-concepts/429926006.json"), Concept.class);
		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem)))
				.thenAnswer(invocation -> {
					List<Long> ids = invocation.getArgument(0);
					if (ids.contains(429926006L)) {
						return List.of(parentConcept);
					}
					return List.of();
				});

		ChangeSummary changeSummary = customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET),
				new ContentJob(new CodeSystem(), "", null), mockSnowstormClient);

		Mockito.verify(mockSnowstormClient).createUpdateBrowserFormatConcepts(conceptListCaptor.capture(), Mockito.eq(codeSystem));
		List<Concept> conceptsSaved = conceptListCaptor.getValue();
		assertEquals(1, conceptsSaved.size());
		assertEquals(externalConceptId, conceptsSaved.get(0).getConceptId());
		assertEquals(1, changeSummary.getAdded());
	}

	@Test
	void createUpdateConcepts_externalConceptIdWhenNotAllowed() throws IOException {
		String dummyModule = "101000003010";
		String externalConceptId = "123456789012345678";
		CodeSystem codeSystem = new CodeSystem("test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule(dummyModule);
		codeSystem.setTranslationLanguages(Map.of());

		ConceptIntent intent = new ConceptIntent("429926006", 1);
		intent.setConceptCode(externalConceptId);
		intent.addTerm("External concept term", Concepts.US_LANG_REFSET);
		List<ConceptIntent> conceptIntents = List.of(intent);

		Concept parentConcept = objectMapper.readValue(getClass().getResourceAsStream("/dummy-concepts/429926006.json"), Concept.class);
		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem)))
				.thenAnswer(invocation -> {
					List<Long> ids = invocation.getArgument(0);
					if (ids.contains(429926006L)) {
						return List.of(parentConcept);
					}
					return List.of();
				});

		ServiceException exception = assertThrows(ServiceException.class, () ->
				customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET),
						new ContentJob(new CodeSystem(), "", null), mockSnowstormClient));

		assertTrue(exception.getMessage().contains("could not be found"));
		assertTrue(exception.getMessage().contains(externalConceptId));
	}

	@Test
	void createUpdateConcepts_inactiveUnknownConceptSkippedWhenMaintainedExternally() throws ServiceException {
		String dummyModule = "101000003010";
		String unknownConceptId = "123456789012345678";
		CodeSystem codeSystem = new CodeSystem("test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule(dummyModule);
		codeSystem.setTranslationLanguages(Map.of());
		codeSystem.setConceptsMaintainedExternally(true);

		ConceptIntent intent = new ConceptIntent("429926006", 1);
		intent.setConceptCode(unknownConceptId);
		intent.setInactive(true);
		List<ConceptIntent> conceptIntents = List.of(intent);

		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem)))
				.thenReturn(List.of());

		ChangeSummary changeSummary = customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET),
				new ContentJob(new CodeSystem(), "", null), mockSnowstormClient);

		Mockito.verify(mockSnowstormClient, never()).createUpdateBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem));
		assertEquals(0, changeSummary.getAdded());
		assertEquals(0, changeSummary.getUpdated());
		assertEquals(0, changeSummary.getRemoved());
	}

	@Test
	void createUpdateConcepts_inactiveUnknownConceptFailsWhenNotMaintainedExternally() {
		String dummyModule = "101000003010";
		String unknownConceptId = "123456789012345678";
		CodeSystem codeSystem = new CodeSystem("test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule(dummyModule);
		codeSystem.setTranslationLanguages(Map.of());

		ConceptIntent intent = new ConceptIntent("429926006", 1);
		intent.setConceptCode(unknownConceptId);
		intent.setInactive(true);
		List<ConceptIntent> conceptIntents = List.of(intent);

		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem)))
				.thenReturn(List.of());

		ServiceException exception = assertThrows(ServiceException.class, () ->
				customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET),
						new ContentJob(new CodeSystem(), "", null), mockSnowstormClient));

		assertTrue(exception.getMessage().contains("could not be found"));
		assertTrue(exception.getMessage().contains(unknownConceptId));
	}

	@Test
	void createUpdateConcepts_inactiveExistingConceptInactivatedWhenMaintainedExternally() throws ServiceException, IOException {
		String dummyModule = "101000003010";
		String existingConceptId = "429926006";
		CodeSystem codeSystem = new CodeSystem("test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule(dummyModule);
		codeSystem.setTranslationLanguages(Map.of());
		codeSystem.setConceptsMaintainedExternally(true);

		ConceptIntent intent = new ConceptIntent(existingConceptId, 1);
		intent.setConceptCode(existingConceptId);
		intent.setInactive(true);
		List<ConceptIntent> conceptIntents = List.of(intent);

		Concept existingConcept = objectMapper.readValue(getClass().getResourceAsStream("/dummy-concepts/429926006.json"), Concept.class);
		existingConcept.setModuleId(dummyModule);
		Mockito.when(mockSnowstormClient.loadBrowserFormatConcepts(Mockito.anyList(), Mockito.eq(codeSystem)))
				.thenReturn(List.of(existingConcept));

		ChangeSummary changeSummary = customConceptService.createUpdateConcepts(codeSystem, conceptIntents, List.of(Concepts.US_LANG_REFSET),
				new ContentJob(new CodeSystem(), "", null), mockSnowstormClient);

		Mockito.verify(mockSnowstormClient).createUpdateBrowserFormatConcepts(conceptListCaptor.capture(), Mockito.eq(codeSystem));
		List<Concept> conceptsSaved = conceptListCaptor.getValue();
		assertEquals(1, conceptsSaved.size());
		assertFalse(conceptsSaved.get(0).isActive());
		assertEquals(1, changeSummary.getRemoved());
	}
}
