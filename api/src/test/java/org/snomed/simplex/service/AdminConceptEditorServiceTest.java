package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.client.domain.DescriptionMini;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.AdminConceptDescriptionDto;
import org.snomed.simplex.rest.pojos.AdminConceptEditorDetail;
import org.snomed.simplex.rest.pojos.AdminConceptUpdateRequest;
import org.snomed.simplex.translation.service.TranslationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConceptEditorServiceTest {

	@Mock
	private SnowstormClient snowstormClient;

	@Mock
	private TranslationService translationService;

	private AdminConceptEditorService service;

	private CodeSystem codeSystem;

	@BeforeEach
	void setUp() {
		service = new AdminConceptEditorService(translationService);
		codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule("101000003010");
		codeSystem.setTranslationLanguages(Map.of(Concepts.US_LANG_REFSET, "en"));
	}

	@Test
	void shortenLangRefsetLabel_removesRedundantRefsetWording() {
		assertEquals("US English", AdminConceptEditorService.shortenLangRefsetLabel("US English synonyms"));
		assertEquals("Belgian French", AdminConceptEditorService.shortenLangRefsetLabel("Belgian French language reference set"));
		assertEquals("Belgian German", AdminConceptEditorService.shortenLangRefsetLabel("Belgian German language refset"));
	}

	@Test
	void loadForEditor_sortsInactiveDescriptionsLast() throws Exception {
		Concept concept = conceptWithDescriptions(
				description("1", "Active FSN (finding)", Description.Type.FSN, true, false),
				description("2", "Inactive synonym", Description.Type.SYNONYM, false, false),
				description("3", "Active synonym", Description.Type.SYNONYM, true, false)
		);
		when(snowstormClient.loadBrowserFormatConcepts(any(), eq(codeSystem))).thenReturn(List.of(concept));
		when(translationService.listTranslations(codeSystem, snowstormClient)).thenReturn(List.of());

		AdminConceptEditorDetail detail = service.loadForEditor(codeSystem, snowstormClient, "123456789");

		assertEquals(List.of("1", "3", "2"), detail.descriptions().stream().map(AdminConceptDescriptionDto::descriptionId).toList());
	}

	@Test
	void applyDescriptionEdits_rejectsReleasedTermChange() {
		Concept concept = conceptWithDescriptions(
				description("1", "Released term (finding)", Description.Type.FSN, true, true)
		);
		when(snowstormClient.loadBrowserFormatConcepts(any(), eq(codeSystem))).thenReturn(List.of(concept));

		AdminConceptUpdateRequest request = new AdminConceptUpdateRequest(List.of(
				new AdminConceptDescriptionDto("1", "Changed term (finding)", "FSN", "en", true, true,
						"101000003010", null, Map.of(Concepts.US_LANG_REFSET, "PREFERRED"))
		));

		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.applyDescriptionEdits(codeSystem, snowstormClient, "123456789", request));
	}

	@Test
	void applyDescriptionEdits_allowsAcceptabilityChangeOnReleasedDescription() throws Exception {
		Description released = description("1", "Released term (finding)", Description.Type.FSN, true, true);
		released.addAcceptability(Concepts.US_LANG_REFSET, Description.Acceptability.PREFERRED);
		Concept concept = conceptWithDescriptions(released);
		when(snowstormClient.loadBrowserFormatConcepts(any(), eq(codeSystem))).thenReturn(List.of(concept));
		when(translationService.listTranslations(codeSystem, snowstormClient)).thenReturn(List.of());

		AdminConceptUpdateRequest request = new AdminConceptUpdateRequest(List.of(
				new AdminConceptDescriptionDto("1", "Released term (finding)", "FSN", "en", true, true,
						"101000003010", null, Map.of(Concepts.US_LANG_REFSET, "ACCEPTABLE"))
		));

		service.applyDescriptionEdits(codeSystem, snowstormClient, "123456789", request);

		ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
		verify(snowstormClient).updateConcept(conceptCaptor.capture(), eq(codeSystem));
		Description saved = conceptCaptor.getValue().getDescriptions().get(0);
		assertEquals(Description.Acceptability.ACCEPTABLE, saved.getAcceptabilityMap().get(Concepts.US_LANG_REFSET));
	}

	@Test
	void applyDescriptionEdits_addsNewSynonymWithDefaultModule() throws Exception {
		Concept concept = conceptWithDescriptions(
				description("1", "Finding (finding)", Description.Type.FSN, true, false)
		);
		when(snowstormClient.loadBrowserFormatConcepts(any(), eq(codeSystem))).thenReturn(List.of(concept));
		when(translationService.listTranslations(codeSystem, snowstormClient)).thenReturn(List.of());
		when(translationService.guessCaseSignificance(eq("New synonym"), any())).thenReturn(Description.CaseSignificance.CASE_INSENSITIVE);

		AdminConceptUpdateRequest request = new AdminConceptUpdateRequest(List.of(
				new AdminConceptDescriptionDto("1", "Finding (finding)", "FSN", "en", true, false,
						"101000003010", null, Map.of(Concepts.US_LANG_REFSET, "PREFERRED")),
				new AdminConceptDescriptionDto(null, "New synonym", "SYNONYM", "en", true, false,
						null, null, Map.of(Concepts.US_LANG_REFSET, "ACCEPTABLE"))
		));

		service.applyDescriptionEdits(codeSystem, snowstormClient, "123456789", request);

		ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
		verify(snowstormClient).updateConcept(conceptCaptor.capture(), eq(codeSystem));
		Description added = conceptCaptor.getValue().getDescriptions().stream()
				.filter(d -> "New synonym".equals(d.getTerm()))
				.findFirst()
				.orElseThrow();
		assertEquals("101000003010", added.getModuleId());
		assertEquals(Description.Type.SYNONYM, added.getType());
	}

	@Test
	void applyDescriptionEdits_delegatesCaseSignificanceWhenUnreleasedTermChanges() throws Exception {
		Description unreleased = description("1", "Old term (finding)", Description.Type.FSN, true, false);
		Concept concept = conceptWithDescriptions(unreleased);
		when(snowstormClient.loadBrowserFormatConcepts(any(), eq(codeSystem))).thenReturn(List.of(concept));
		when(translationService.listTranslations(codeSystem, snowstormClient)).thenReturn(List.of());
		when(translationService.guessCaseSignificance(eq("New term (finding)"), any()))
				.thenReturn(Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);

		AdminConceptUpdateRequest request = new AdminConceptUpdateRequest(List.of(
				new AdminConceptDescriptionDto("1", "New term (finding)", "FSN", "en", true, false,
						"101000003010", null, Map.of(Concepts.US_LANG_REFSET, "PREFERRED"))
		));

		service.applyDescriptionEdits(codeSystem, snowstormClient, "123456789", request);

		verify(translationService).guessCaseSignificance(eq("New term (finding)"), any());
		ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
		verify(snowstormClient).updateConcept(conceptCaptor.capture(), eq(codeSystem));
		Description saved = conceptCaptor.getValue().getDescriptions().get(0);
		assertEquals(Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, saved.getCaseSignificance());
	}

	private static Concept conceptWithDescriptions(Description... descriptions) {
		Concept concept = new Concept("101000003010");
		concept.setConceptId("123456789");
		concept.setFsn(new DescriptionMini("Finding (finding)", "en"));
		List<Description> list = new ArrayList<>(List.of(descriptions));
		concept.setDescriptions(list);
		return concept;
	}

	private static Description description(String id, String term, Description.Type type, boolean active, boolean released) {
		Description description = new Description(type, "en", term, Description.CaseSignificance.CASE_INSENSITIVE);
		description.setDescriptionId(id);
		description.setActive(active);
		description.setReleased(released);
		description.setModuleId("101000003010");
		return description;
	}
}
