package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.FsnBulkRemovalStats;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.client.domain.DescriptionMini;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.DeleteFsnDescriptionsResult;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFsnDeletionServiceTest {

	@Mock
	private SnowstormClient snowstormClient;

	private AdminFsnDeletionService service;

	private CodeSystem codeSystem;

	@BeforeEach
	void setUp() {
		service = new AdminFsnDeletionService();
		codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDefaultModule("101000003010");
	}

	@Test
	void findConceptsWithoutActiveEnFsn_returnsDefaultModuleConceptsMissingUsPreferredEnFsn() throws Exception {
		when(snowstormClient.collectConceptIdsInModule(codeSystem, "101000003010"))
				.thenReturn(new LinkedHashSet<>(Set.of("100", "200", "300")));
		when(snowstormClient.collectConceptIdsWithUsPreferredActiveEnFsn(codeSystem, "101000003010"))
				.thenReturn(new LinkedHashSet<>(Set.of("200")));

		ConceptMini concept100 = conceptMini("100", "Concept one");
		when(snowstormClient.getConceptMini("100", codeSystem)).thenReturn(concept100);

		Page<ConceptMini> page = service.findConceptsWithoutActiveEnFsn(codeSystem, snowstormClient, 0, 1);

		assertEquals(2L, page.getTotal());
		assertEquals(1, page.getItems().size());
		assertEquals("100", page.getItems().get(0).getConceptId());
	}

	@Test
	void findConceptsWithoutActiveEnFsn_slicesResultsByOffsetAndLimit() throws Exception {
		when(snowstormClient.collectConceptIdsInModule(codeSystem, "101000003010"))
				.thenReturn(new LinkedHashSet<>(Set.of("100", "300")));
		when(snowstormClient.collectConceptIdsWithUsPreferredActiveEnFsn(codeSystem, "101000003010"))
				.thenReturn(new LinkedHashSet<>());

		ConceptMini concept300 = conceptMini("300", "Concept three");
		when(snowstormClient.getConceptMini("300", codeSystem)).thenReturn(concept300);

		Page<ConceptMini> page = service.findConceptsWithoutActiveEnFsn(codeSystem, snowstormClient, 1, 1);

		assertEquals(2L, page.getTotal());
		assertEquals(1, page.getItems().size());
		assertEquals("300", page.getItems().get(0).getConceptId());
	}

	private static ConceptMini conceptMini(String conceptId, String term) {
		return new ConceptMini(conceptId, new DescriptionMini(term, "en"));
	}

	@Test
	void deleteAllFsnDescriptions_rejectsMissingDefaultModule() {
		codeSystem.setDefaultModule(null);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.deleteAllFsnDescriptions(codeSystem, snowstormClient, false, null));

		assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode());
	}

	@Test
	void deleteAllFsnDescriptions_dryRunDoesNotBulkUpdate() throws Exception {
		when(snowstormClient.collectActiveFsnConceptIds(codeSystem, "101000003010", null))
				.thenReturn(new LinkedHashSet<>(List.of(123456789L, 987654321L)));

		DeleteFsnDescriptionsResult result = service.deleteAllFsnDescriptions(codeSystem, snowstormClient, true, null);

		assertEquals(2, result.conceptsWithFsn());
		assertEquals(0, result.conceptsUpdated());
	}

	@Test
	void deleteAllFsnDescriptions_runsBulkRemovalForDiscoveredConcepts() throws Exception {
		when(snowstormClient.collectActiveFsnConceptIds(codeSystem, "101000003010", null))
				.thenReturn(new LinkedHashSet<>(List.of(123456789L)));

		FsnBulkRemovalStats stats = new FsnBulkRemovalStats();
		stats.incrementConceptsUpdated();
		stats.incrementFsnDeleted();
		when(snowstormClient.bulkRemoveDescriptionsMatching(eq(codeSystem), any(), any(), eq("Removing FSN descriptions")))
				.thenReturn(stats);

		DeleteFsnDescriptionsResult result = service.deleteAllFsnDescriptions(codeSystem, snowstormClient, false, null);

		assertEquals(1, result.conceptsWithFsn());
		assertEquals(1, result.conceptsUpdated());
		assertEquals(1, result.fsnDeleted());

		ArgumentCaptor<Predicate<Description>> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
		verify(snowstormClient).bulkRemoveDescriptionsMatching(eq(codeSystem), any(), predicateCaptor.capture(), eq("Removing FSN descriptions"));

		Description matchingFsn = new Description(Description.Type.FSN, "en", "Finding (finding)", null);
		matchingFsn.setActive(true);
		matchingFsn.setModuleId("101000003010");

		Description otherModule = new Description(Description.Type.FSN, "en", "Other (finding)", null);
		otherModule.setActive(true);
		otherModule.setModuleId("999999999999");

		assertEquals(true, predicateCaptor.getValue().test(matchingFsn));
		assertEquals(false, predicateCaptor.getValue().test(otherModule));
	}

	@Test
	void deleteAllFsnDescriptions_languageFilterLimitsDiscoveryAndRemoval() throws Exception {
		when(snowstormClient.collectActiveFsnConceptIds(codeSystem, "101000003010", "fr"))
				.thenReturn(new LinkedHashSet<>(List.of(123456789L)));
		when(snowstormClient.bulkRemoveDescriptionsMatching(eq(codeSystem), any(), any(), eq("Removing FSN descriptions")))
				.thenReturn(new FsnBulkRemovalStats());

		service.deleteAllFsnDescriptions(codeSystem, snowstormClient, false, " FR ");

		verify(snowstormClient).collectActiveFsnConceptIds(codeSystem, "101000003010", "fr");

		ArgumentCaptor<Predicate<Description>> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
		verify(snowstormClient).bulkRemoveDescriptionsMatching(eq(codeSystem), any(), predicateCaptor.capture(), eq("Removing FSN descriptions"));

		Description frenchFsn = new Description(Description.Type.FSN, "fr", "Trouvaille (finding)", null);
		frenchFsn.setActive(true);
		frenchFsn.setModuleId("101000003010");

		Description englishFsn = new Description(Description.Type.FSN, "en", "Finding (finding)", null);
		englishFsn.setActive(true);
		englishFsn.setModuleId("101000003010");

		assertEquals(true, predicateCaptor.getValue().test(frenchFsn));
		assertEquals(false, predicateCaptor.getValue().test(englishFsn));
	}
}
