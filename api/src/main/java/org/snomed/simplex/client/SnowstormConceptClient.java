package org.snomed.simplex.client;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.util.CollectionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.simplex.client.domain.Description.Acceptability.PREFERRED;
import static org.snomed.simplex.client.domain.Description.CaseSignificance.CASE_INSENSITIVE;
import static org.snomed.simplex.client.domain.Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
import static org.snomed.simplex.client.domain.Description.Type.FSN;
import static org.snomed.simplex.client.domain.Description.Type.SYNONYM;
import static org.snomed.simplex.client.SnowstormClientSupport.getServiceException;
import static org.snomed.simplex.client.SnowstormClientSupport.requireBody;
import static org.snomed.simplex.client.SnowstormClient.MAX_PAGE_SIZE;
import static org.snomed.simplex.client.SnowstormClient.CONCEPT_ENDPOINT;
import static org.snomed.simplex.client.SnowstormClient.CONCEPTS_SEARCH_ENDPOINT;

class SnowstormConceptClient {

	private static final Logger logger = LoggerFactory.getLogger(SnowstormConceptClient.class);

	private final ParameterizedTypeReference<Page<Long>> responseTypeSCTIDPage = new ParameterizedTypeReference<>(){};

	private final RestTemplate restTemplate;
	private final SnowstormJobClient jobClient;

	SnowstormConceptClient(RestTemplate restTemplate, SnowstormJobClient jobClient) {
		this.restTemplate = restTemplate;
		this.jobClient = jobClient;
	}

	public ConceptMini getConceptMini(String conceptId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		try {
			ResponseEntity<ConceptMini> response = restTemplate.getForEntity(
					format(CONCEPT_ENDPOINT, codeSystem.getWorkingBranchPath(), conceptId), ConceptMini.class);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			throw new ServiceExceptionWithStatusCode("Concept not found: %s".formatted(conceptId), HttpStatus.NOT_FOUND);
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concept");
		}
	}

	public Concept createSimpleMetadataConcept(String parentConceptId, String preferredTerm, String tag, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		Concept concept = newSimpleMetadataConceptWithoutSave(parentConceptId, preferredTerm, tag);
		return createConcept(concept, codeSystem);
	}

	public Concept createConcept(Concept concept, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		try {
			ResponseEntity<Concept> response = restTemplate.exchange(format("/browser/%s/concepts", codeSystem.getWorkingBranchPath()), HttpMethod.POST, new HttpEntity<>(concept), Concept.class);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "create concept");
		}
	}

	public Concept newSimpleMetadataConceptWithoutSave(String parentConceptId, String preferredTerm, String tag) {
		Description.CaseSignificance caseSens = guessCaseSensitivity(preferredTerm);
		return new Concept(null)
				.addDescription(new Description(FSN, "en", format("%s (%s)", preferredTerm, tag), caseSens, Concepts.US_LANG_REFSET, PREFERRED))
				.addDescription(new Description(SYNONYM, "en", preferredTerm, caseSens, Concepts.US_LANG_REFSET, PREFERRED))
				.addAxiom(new Axiom("PRIMITIVE", Collections.singletonList(Relationship.stated(Concepts.IS_A, parentConceptId, 0))))
				.addRelationship(Relationship.inferred(Concepts.IS_A, parentConceptId, 0));
	}

	public void deleteConcept(String conceptId, CodeSystem codeSystem) {
		restTemplate.delete(format(CONCEPT_ENDPOINT, codeSystem.getWorkingBranchPath(), conceptId));
	}

	public Concept updateConcept(Concept concept, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		try {
			ResponseEntity<Concept> response = restTemplate.exchange(format("/browser/%s/concepts/%s", codeSystem.getWorkingBranchPath(), concept.getConceptId()),
					HttpMethod.PUT, new HttpEntity<>(concept), Concept.class);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "update concept");
		}
	}

	public List<Long> getConceptIds(Collection<String> conceptIds, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		if (conceptIds.isEmpty()) {
			return new ArrayList<>();
		}

		try {
			List<Long> results = null;
			for (List<String> conceptIdBatch : Iterables.partition(conceptIds, MAX_PAGE_SIZE)) {
				ConceptSearchRequest searchRequest = new ConceptSearchRequest(conceptIdBatch);
				searchRequest.setReturnIdOnly(true);
				searchRequest.setActiveFilter(true);
				searchRequest.setLimit(MAX_PAGE_SIZE);

				ResponseEntity<Page<Long>> pageOfIds = restTemplate.exchange(format(CONCEPTS_SEARCH_ENDPOINT, codeSystem.getWorkingBranchPath()), HttpMethod.POST,
						new HttpEntity<>(searchRequest), responseTypeSCTIDPage);
				Page<Long> page = requireBody(pageOfIds.getBody(), "concept search results");
				if (results == null) {
					results = new ArrayList<>(page.getItems());
				} else {
					results.addAll(page.getItems());
				}
			}
			return results;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concept ids");
		}
	}

	public List<Concept> loadBrowserFormatConcepts(List<Long> conceptIds, CodeSystem codeSystem) {
		ParameterizedTypeReference<List<Concept>> listOfConcepts = new ParameterizedTypeReference<>(){};
		ResponseEntity<List<Concept>> response = restTemplate.exchange(format("/browser/%s/concepts/bulk-load", codeSystem.getWorkingBranchPath()), HttpMethod.POST,
				new HttpEntity<>(ConceptBulkLoadRequest.ofConceptIds(conceptIds)), listOfConcepts);
		return response.getBody();
	}

	public List<Concept> loadBrowserFormatConceptsByDescriptionIds(Collection<String> descriptionIds, CodeSystem codeSystem) {
		if (descriptionIds.isEmpty()) {
			return List.of();
		}
		ParameterizedTypeReference<List<Concept>> listOfConcepts = new ParameterizedTypeReference<>(){};
		List<Concept> results = new ArrayList<>();
		for (List<String> batch : Lists.partition(new ArrayList<>(descriptionIds), 200)) {
			ResponseEntity<List<Concept>> response = restTemplate.exchange(
					format("/browser/%s/concepts/bulk-load", codeSystem.getWorkingBranchPath()), HttpMethod.POST,
					new HttpEntity<>(ConceptBulkLoadRequest.ofDescriptionIds(batch)), listOfConcepts);
			if (response.getBody() != null) {
				results.addAll(response.getBody());
			}
		}
		return results;
	}

	public Map<String, Boolean> loadDescriptionActiveFlags(Collection<String> descriptionIds, CodeSystem codeSystem) {
		if (descriptionIds.isEmpty()) {
			return Map.of();
		}
		Set<String> requested = new HashSet<>(descriptionIds);
		Map<String, Boolean> flags = new HashMap<>();
		for (List<String> batch : Lists.partition(new ArrayList<>(requested), 200)) {
			for (Concept concept : CollectionUtils.orEmpty(loadBrowserFormatConceptsByDescriptionIds(batch, codeSystem))) {
				for (Description description : CollectionUtils.orEmpty(concept.getDescriptions())) {
					String descriptionId = description.getDescriptionId();
					if (requested.contains(descriptionId)) {
						flags.put(descriptionId, description.isActive());
					}
				}
			}
		}
		return flags;
	}

	public List<Concept> loadBrowserFormatConceptsUsingGet(List<Long> conceptIds, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		ParameterizedTypeReference<Page<Concept>> pageOfConcepts = new ParameterizedTypeReference<>(){};
		Map<String, String> params = new HashMap<>();
		params.put("conceptIds", Strings.join(conceptIds, ','));
		ResponseEntity<Page<Concept>> response = restTemplate.exchange(format("/browser/%s/concepts?conceptIds={conceptIds}", codeSystem.getWorkingBranchPath()), HttpMethod.GET,
				null, pageOfConcepts, params);
		Page<Concept> body = requireBody(response.getBody(), "concepts");
		return body.getItems();
	}

	public void createUpdateBrowserFormatConcepts(List<Concept> conceptsToUpdate, CodeSystem codeSystem) throws ServiceException {
		String branchPath = codeSystem.getWorkingBranchPath();
		if (conceptsToUpdate == null || conceptsToUpdate.isEmpty()) {
			return;
		}

		List<Concept> conceptsToDelete = new ArrayList<>();
		List<Concept> conceptsForBulkUpdate = new ArrayList<>();
		for (Concept concept : conceptsToUpdate) {
			if (!concept.isActive() && !concept.isReleased()) {
				conceptsToDelete.add(concept);
			} else {
				concept.setDescriptions(getNewDescriptionSet(concept));
				conceptsForBulkUpdate.add(concept);
			}
		}

		deleteConcepts(conceptsToDelete, branchPath);

		// Start an async bulk update job
		if (!conceptsForBulkUpdate.isEmpty()) {
			logger.info("Starting bulk create/update on {}", branchPath);
			ResponseEntity<Void> response = restTemplate.exchange(format("/browser/%s/concepts/bulk", branchPath), HttpMethod.POST,
					new HttpEntity<>(conceptsForBulkUpdate), Void.class);
			URI location = response.getHeaders().getLocation();
			if (location == null) {
				throw new ServiceException("Bulk update did not return location header.");
			}
			jobClient.waitForAsyncJob(location, "COMPLETED", "FAILED");
			logger.info("Completed bulk create/update on {}", branchPath);
		}
	}

	public void bulkChangeDescriptions(Map<Long, Set<String>> conceptDescriptionIdMap,
			CodeSystem codeSystem, Predicate<Description> processDescription, String changeForLogMessage) throws ServiceException {

		List<Long> conceptIds = new ArrayList<>(conceptDescriptionIdMap.keySet());
		for (List<Long> conceptIdBatch : Lists.partition(conceptIds, 200)) {
			List<Concept> concepts = loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
			bulkChangeDescriptionBatch(conceptDescriptionIdMap, codeSystem, processDescription, changeForLogMessage, concepts);
		}
	}

	public FsnBulkRemovalStats bulkRemoveDescriptionsMatching(CodeSystem codeSystem, Collection<Long> conceptIds,
			Predicate<Description> shouldRemove, String changeForLogMessage) throws ServiceException {

		FsnBulkRemovalStats totalStats = new FsnBulkRemovalStats();
		if (conceptIds == null || conceptIds.isEmpty()) {
			return totalStats;
		}
		List<Long> conceptIdList = conceptIds instanceof List<Long> list ? list : new ArrayList<>(conceptIds);
		for (List<Long> conceptIdBatch : Lists.partition(conceptIdList, 200)) {
			totalStats.add(bulkRemoveDescriptionsBatch(codeSystem, conceptIdBatch, shouldRemove, changeForLogMessage));
		}
		return totalStats;
	}

	private Description.CaseSignificance guessCaseSensitivity(String name) {
		String termWithoutFirstChar = name.substring(1);
		return termWithoutFirstChar.equals(termWithoutFirstChar.toLowerCase(Locale.ROOT)) ? CASE_INSENSITIVE : ENTIRE_TERM_CASE_SENSITIVE;
	}

	private void deleteConcepts(List<Concept> conceptsToDelete, String branchPath) {
		for (Concept conceptToDelete : conceptsToDelete) {
			String conceptId = conceptToDelete.getConceptId();
			logger.info("Deleting concept {} on {}", conceptId, branchPath);
			restTemplate.delete(format(CONCEPT_ENDPOINT, branchPath, conceptId));
		}
	}

	private static List<Description> getNewDescriptionSet(Concept concept) {
		List<Description> newDescriptionSet = new ArrayList<>();
		for (Description description : concept.getDescriptions()) {
			if (description.isRemove()) {
				if (description.isReleased()) {
					description.setActive(false);
					newDescriptionSet.add(description);
				}
				// else don't retain description
			} else {
				newDescriptionSet.add(description);
			}
		}
		return newDescriptionSet;
	}

	private void bulkChangeDescriptionBatch(Map<Long, Set<String>> conceptDescriptionIdMap, CodeSystem codeSystem,
			Predicate<Description> processDescription, String changeForLogMessage,
			List<Concept> concepts) throws ServiceException {

		Map<Long, Concept> conceptsToSave = new HashMap<>();
		for (Concept concept : concepts) {
			Set<String> conceptDescriptionIds = conceptDescriptionIdMap.get(concept.getConceptIdAsLong());
			for (Description description : concept.getDescriptions()) {
				if (conceptDescriptionIds.contains(description.getDescriptionId())
						&& processDescription.test(description)) {
					conceptsToSave.put(concept.getConceptIdAsLong(), concept);
				}
			}
		}
		if (!conceptsToSave.isEmpty()) {
			logger.info("{} on {} concepts.", changeForLogMessage, conceptsToSave.size());
			createUpdateBrowserFormatConcepts(new ArrayList<>(conceptsToSave.values()), codeSystem);
		}
	}

	private FsnBulkRemovalStats bulkRemoveDescriptionsBatch(CodeSystem codeSystem, List<Long> conceptIdBatch,
			Predicate<Description> shouldRemove, String changeForLogMessage) throws ServiceException {

		FsnBulkRemovalStats batchStats = new FsnBulkRemovalStats();
		List<Concept> concepts = loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
		List<Concept> conceptsToSave = new ArrayList<>();
		for (Concept concept : concepts) {
			boolean conceptChanged = false;
			for (Description description : concept.getDescriptions()) {
				if (!shouldRemove.test(description)) {
					continue;
				}
				DescriptionRemoval.Outcome outcome = DescriptionRemoval.markForRemoval(description);
				switch (outcome) {
					case INACTIVATED -> batchStats.incrementFsnInactivated();
					case DELETED -> batchStats.incrementFsnDeleted();
					case UNCHANGED -> batchStats.incrementFsnSkipped();
				}
				if (outcome != DescriptionRemoval.Outcome.UNCHANGED) {
					conceptChanged = true;
				}
			}
			if (conceptChanged) {
				conceptsToSave.add(concept);
				batchStats.incrementConceptsUpdated();
			}
		}
		if (!conceptsToSave.isEmpty()) {
			logger.info("{} on {} concepts.", changeForLogMessage, conceptsToSave.size());
			createUpdateBrowserFormatConcepts(conceptsToSave, codeSystem);
		}
		return batchStats;
	}

	private record ConceptBulkLoadRequest(Set<String> conceptIds, Set<String> descriptionIds) {

		static ConceptBulkLoadRequest ofConceptIds(Collection<Long> conceptIds) {
			return new ConceptBulkLoadRequest(
					conceptIds.stream().map(Objects::toString).collect(Collectors.toSet()), Set.of());
		}

		static ConceptBulkLoadRequest ofDescriptionIds(Collection<String> descriptionIds) {
			return new ConceptBulkLoadRequest(Set.of(), new HashSet<>(descriptionIds));
		}

	}
}
