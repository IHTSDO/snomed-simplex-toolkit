package com.snomed.simplextoolkit.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.exceptions.ClientException;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.StreamUtils;
import com.snomed.simplextoolkit.util.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.snomed.simplextoolkit.client.domain.Description.Acceptability.PREFERRED;
import static com.snomed.simplextoolkit.client.domain.Description.CaseSignificance.CASE_INSENSITIVE;
import static com.snomed.simplextoolkit.client.domain.Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
import static com.snomed.simplextoolkit.client.domain.Description.Type.FSN;
import static com.snomed.simplextoolkit.client.domain.Description.Type.SYNONYM;
import static java.lang.String.format;

public class SnowstormClient {

	public static final int MAX_PAGE_SIZE = 10_000;
	private final ParameterizedTypeReference<Map<String, Object>> responseTypeMap = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<RefsetMember>> responseTypeRefsetPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<CodeSystem>> responseTypeCodeSystemPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<ConceptMini>> responseTypeConceptMiniPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<Long>> responseTypeSCTIDPage = new ParameterizedTypeReference<>(){};

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	private static final Logger logger = LoggerFactory.getLogger(SnowstormClient.class);

	public SnowstormClient(String snowstormUrl, String authenticationToken, ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;

		if (Strings.isBlank(snowstormUrl)) {
			throw new IllegalStateException("Snowstorm URL is not yet configured");
		}
		restTemplate = new RestTemplateBuilder()
				.rootUri(snowstormUrl)
				.defaultHeader("Cookie", authenticationToken)
				.build();
	}

	public void ping() throws ServiceException {
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/version", HttpMethod.GET, null, responseTypeMap);
			if (response.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> body = response.getBody();
				System.out.printf("Pinged Snowstorm successfully, version %s%n", body != null ? body.get("version") : "body is null");
			}
		} catch (HttpStatusCodeException e) {
			throw new IllegalStateException("Could not connect to Snowstorm");
		}
	}

	public List<CodeSystem> getCodeSystems() throws ServiceException {
		try {
			ResponseEntity<Page<CodeSystem>> response = restTemplate.exchange("/codesystems", HttpMethod.GET, null, responseTypeCodeSystemPage);
			return response.getBody().getItems().stream().filter(Predicate.not(CodeSystem::isPostcoordinated)).collect(Collectors.toList());
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "list code systems");
		}
	}

	public CodeSystem getCodeSystemForDisplay(String codesystemShortName) throws ServiceException {
		CodeSystem codeSystem = getCodeSystemOrThrow(codesystemShortName);
		codeSystem.setDefaultModuleDisplay(getPT(codeSystem, codeSystem.getDefaultModuleOrThrow()).orElse("Concept not found"));
		return codeSystem;
	}

	private Optional<String> getPT(CodeSystem codeSystem, String conceptId) {
		try {
			ResponseEntity<ConceptMini> response = restTemplate.getForEntity(format("/%s/concepts/%s", codeSystem.getBranchPath(), conceptId), ConceptMini.class);
			ConceptMini conceptMini = response.getBody();
			if (conceptMini == null) {
				return Optional.empty();
			}
			return Optional.of(conceptMini.getPtOrFsnOrConceptId());
		} catch (HttpClientErrorException e) {
			return Optional.empty();
		}
	}

	public CodeSystem getCodeSystemOrThrow(String codesystemShortName) throws ServiceException {
		try {
			ResponseEntity<CodeSystem> response = restTemplate.getForEntity(format("/codesystems/%s", codesystemShortName), CodeSystem.class);
			CodeSystem codeSystem = response.getBody();
			if (codeSystem == null) {
				throw new ServiceException(format("Code System not found %s", codesystemShortName));
			}
			String branchPath = codeSystem.getBranchPath();
			ResponseEntity<Branch> branchResponse = restTemplate.getForEntity(format("/branches/%s", branchPath), Branch.class);
			Branch branch = branchResponse.getBody();
			if (branch == null) {
				throw new ServiceException(format("Branch not found %s", branchPath));
			}
			codeSystem.setDefaultModule(branch.getDefaultModule());

			return codeSystem;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load code system");
		}
	}

	public CodeSystem createCodeSystem(String name, String shortName, String namespace) throws ClientException {
		String branchPath = "MAIN/" + shortName;
		try {
			restTemplate.exchange("/codesystems", HttpMethod.POST, new HttpEntity<>(new CodeSystem(name, shortName, branchPath)), CodeSystem.class);
			CodeSystem codeSystem = restTemplate.getForEntity(format("/codesystems/%s", shortName), CodeSystem.class).getBody();

			// Set namespace
			Map<String, String> newBranchMetadata = new HashMap<>();
			newBranchMetadata.put("defaultNamespace", namespace);
			addBranchMetadata(branchPath, newBranchMetadata);

			return codeSystem;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "create code system");
		}
	}

	public void deleteCodeSystem(String shortName) {
		restTemplate.delete(format("/codesystems/%s", shortName));
	}

	public void addBranchMetadata(String branchPath, Map<String, String> newBranchMetadata) {
		restTemplate.exchange(format("/branches/%s/metadata-upsert", branchPath), HttpMethod.PUT, new HttpEntity<>(newBranchMetadata), Map.class);
	}

	public void deleteBranchAndChildren(String branchPath) {
		ResponseEntity<List<Branch>> children = restTemplate.exchange(format("/branches/%s/children?immediateChildren=true", branchPath), HttpMethod.GET,
				new HttpEntity<>(Void.class), new ParameterizedTypeReference<>() {});
		for (Branch childBranch : CollectionUtils.orEmpty(children.getBody())) {
			deleteBranchAndChildren(childBranch.getPath());
		}

		restTemplate.delete(format("/admin/%s/actions/hard-delete", branchPath));
	}

	public ConceptMini getRefsetOrThrow(String refsetId, CodeSystem codeSystem) throws ServiceException {
		ConceptMini refset = getRefset(refsetId, codeSystem);
		if (refset == null) {
			throw new IllegalArgumentException("Refset not found.");
		}
		return refset;
	}

	public ConceptMini getRefset(String refsetId, CodeSystem codeSystem) throws ServiceException {
		List<ConceptMini> refsets = getRefsets(refsetId, codeSystem);
		if (!refsets.isEmpty()) {
			return refsets.get(0);
		}
		return null;
	}

	public List<ConceptMini> getRefsets(String refsetEcl, CodeSystem codeSystem) throws ServiceException {
		// Get refset concepts from code system branch and module
		Map<String, ConceptMini> refsetConceptMap = getConcepts(refsetEcl, codeSystem).getItems().stream()
				.peek(conceptMini -> conceptMini.setActiveMemberCount(0L))
				.collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity()));

		// Join active refset counts
		String url = format("/browser/%s/members?active=true&referenceSet=%s", codeSystem.getBranchPath(), refsetEcl);
		try {
			ResponseEntity<RefsetAggregationPage> response = restTemplate.exchange(url, HttpMethod.GET, null, RefsetAggregationPage.class);
			List<ConceptMini> refsetsWithActiveMemberCount = response.getBody().getRefsetsWithActiveMemberCount();
			for (ConceptMini refset : refsetsWithActiveMemberCount) {
				if (refsetConceptMap.containsKey(refset.getConceptId())) {
					refsetConceptMap.get(refset.getConceptId()).setActiveMemberCount(refset.getActiveMemberCount());
				}
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch refset list");
		}

		ArrayList<ConceptMini> refsetConcepts = new ArrayList<>(refsetConceptMap.values());
		refsetConcepts.sort(Comparator.comparing(ConceptMini::getPtOrFsnOrConceptId));
		return refsetConcepts;
	}

	private Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem) throws ServiceException {
		try {
			String url = format("/%s/concepts?ecl=%s&module=%s&limit=300", codeSystem.getBranchPath(), ecl, codeSystem.getDefaultModuleOrThrow());
			ResponseEntity<Page<ConceptMini>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseTypeConceptMiniPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts");
		}
	}

	public Page<RefsetMember> getRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly, int offset, int limit) throws ClientException {
		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&offset=%s&limit=%s%s",
							codeSystem.getBranchPath(), refsetId, offset, limit, activeOnly ? "&active=true" : ""),
					HttpMethod.GET, null, responseTypeRefsetPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load refset");
		}
	}

	public List<RefsetMember> loadAllRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly) throws ServiceException {
		Page<RefsetMember> page = getRefsetMembers(refsetId, codeSystem, activeOnly, 0, MAX_PAGE_SIZE);
		if (page.getTotal() > page.getItems().size()) {
			// TODO: Load the rest. Will need to implement scrolling beyond 10K members in Snowstorm.
			logger.warn("WARNING, only the first 10K members were loaded! There are {} in total!", page.getTotal());
		}
		return page.getItems();
	}

	public int countAllActiveRefsetMembers(String refsetId, CodeSystem codeSystem) {
		ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&active=true&limit=1", codeSystem.getBranchPath(), refsetId),
				HttpMethod.GET,null, responseTypeRefsetPage);
		Page<RefsetMember> page = response.getBody();
		return Math.toIntExact(page.getTotal());
	}

	public void createUpdateRefsetMembers(List<RefsetMember> membersToCreateUpdate, CodeSystem codeSystem) throws ServiceException {

		List<String> memberIds = membersToCreateUpdate.stream().map(RefsetMember::getMemberId).collect(Collectors.toList());
		Set<String> memberIdSet = new HashSet<>();
		for (String memberId : memberIds) {
			if (!memberIdSet.add(memberId)) {
				throw new IllegalArgumentException("create/update request contains a duplicate member id " + memberId);
			}
		}

		URI bulkJobUri;
		try {
			bulkJobUri = restTemplate.postForLocation(format("/%s/members/bulk", codeSystem.getBranchPath()), membersToCreateUpdate);
			if (bulkJobUri == null) {
				throw new ServiceException("Failed to start bulk create/update refset member job - response location is null.");
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "start bulk create/update refset member job");
		}
		try {
			while (true) {
				ResponseEntity<StatusHolder> response = restTemplate.getForEntity(bulkJobUri, StatusHolder.class);
				StatusHolder statusHolder = response.getBody();
				String status = statusHolder.getStatus();
				if ("RUNNING".equals(status)) {
					System.out.print(".");
				} else if ("COMPLETED".equals(status)) {
					return;
				} else {
					throw new ServiceException(format("Bulk create/update refset member job failed - %s", statusHolder.getMessage()));
				}
				Thread.sleep(1_000);// One second
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch status of bulk create/update refset member job");
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteRefsetMembers(List<RefsetMember> membersToDelete, CodeSystem codeSystem) throws ServiceException {
		List<String> memberIds = membersToDelete.stream().map(RefsetMember::getMemberId).collect(Collectors.toList());
		Map<String, List<String>> bulkDeleteRequest = new HashMap<>();
		bulkDeleteRequest.put("memberIds", memberIds);
		try {
			restTemplate.exchange(format("/%s/members", codeSystem.getBranchPath()), HttpMethod.DELETE, new HttpEntity<>(bulkDeleteRequest), Void.class);
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "bulk delete refset members");
		}
	}

	public Concept createSimpleMetadataConcept(String parentConceptId, String preferredTerm, String tag, CodeSystem codeSystem) throws ClientException {
		Concept concept = newSimpleMetadataConceptWithoutSave(parentConceptId, preferredTerm, tag);
		return createConcept(concept, codeSystem);
	}

	public Concept createConcept(Concept concept, CodeSystem codeSystem) throws ClientException {
		try {
			ResponseEntity<Concept> response = restTemplate.exchange(format("/browser/%s/concepts", codeSystem.getBranchPath()), HttpMethod.POST, new HttpEntity<>(concept), Concept.class);
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

	public void deleteConcept(Concept concept, CodeSystem codeSystem) {
		restTemplate.delete(format("/%s/concepts/%s", codeSystem.getBranchPath(), concept.getConceptId()));
	}

	public Concept updateConcept(Concept concept, CodeSystem codeSystem) throws ClientException {
		try {
			ResponseEntity<Concept> response = restTemplate.exchange(format("/browser/%s/concepts/%s", codeSystem.getBranchPath(), concept.getConceptId()),
					HttpMethod.PUT, new HttpEntity<>(concept), Concept.class);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "update concept");
		}
	}

	public List<Long> getConceptIds(Collection<String> conceptIds, CodeSystem codeSystem) throws ServiceException {
		if (conceptIds.isEmpty()) {
			return new ArrayList<>();
		}

		ConceptSearchRequest searchRequest = new ConceptSearchRequest(conceptIds);
		searchRequest.setReturnIdOnly(true);
		searchRequest.setActiveFilter(true);
		searchRequest.setLimit(MAX_PAGE_SIZE);
		try {
			ResponseEntity<Page<Long>> pageOfIds = restTemplate.exchange(format("/%s/concepts/search", codeSystem.getBranchPath()), HttpMethod.POST,
					new HttpEntity<>(searchRequest), responseTypeSCTIDPage);
			Page<Long> page = pageOfIds.getBody();
			if (page != null && page.getTotal() > page.getItems().size()) {
				// FIXME: Limited to 10K
				throw new ServiceException("Failed to load concept list greater than 10K");
			}
			return page.getItems();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concept ids");
		}
	}

	private ClientException getServiceException(HttpStatusCodeException e, String action) {
		return new ClientException(format("Failed to %s", action), e);
	}

	public void exportRF2(OutputStream outputStream, String snowstormExportType, CodeSystem codeSystem) throws ServiceException {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("branchPath", codeSystem.getBranchPath());
		requestBody.put("type", snowstormExportType);
		URI location = restTemplate.execute("/exports", HttpMethod.POST,
				httpRequest -> {
					httpRequest.getHeaders().add("Content-Type", "application/json");
					objectMapper.writeValue(System.out, requestBody);
					objectMapper.writeValue(httpRequest.getBody(), requestBody);
				},
				httpResponse -> httpResponse.getHeaders().getLocation());
		if (location == null) {
			throw new ServiceException("Snowstorm did not return a location header for the RF2 export.");
		}
		restTemplate.execute(location + "/archive", HttpMethod.GET, null, httpResponse -> {
			StreamUtils.copyViaTempFile(httpResponse.getBody(), outputStream, false);
			return null;
		});
	}

	private Description.CaseSignificance guessCaseSensitivity(String name) {
		String termWithoutFirstChar = name.substring(1);
		return termWithoutFirstChar.equals(termWithoutFirstChar.toLowerCase(Locale.ROOT)) ? CASE_INSENSITIVE : ENTIRE_TERM_CASE_SENSITIVE;
	}

	public List<Concept> loadBrowserFormatConcepts(List<Long> conceptIds, CodeSystem codeSystem) {
		ParameterizedTypeReference<List<Concept>> listOfConcepts = new ParameterizedTypeReference<>(){};
		ResponseEntity<List<Concept>> response = restTemplate.exchange(format("/browser/%s/concepts/bulk-load", codeSystem.getBranchPath()), HttpMethod.POST,
				new HttpEntity<>(new ConceptBulkLoadRequest(conceptIds)), listOfConcepts);
		return response.getBody();
	}

	public void updateBrowserFormatConcepts(List<Concept> conceptsToUpdate, CodeSystem codeSystem) throws ServiceException {
		if (conceptsToUpdate == null || conceptsToUpdate.isEmpty()) {
			return;
		}
		// Start an async bulk update job
		ResponseEntity<Void> response = restTemplate.exchange(format("/browser/%s/concepts/bulk", codeSystem.getBranchPath()), HttpMethod.POST,
				new HttpEntity<>(conceptsToUpdate), Void.class);
		URI location = response.getHeaders().getLocation();
		if (location == null) {
			throw new ServiceException("Bulk update did not return location header.");
		}

		waitForAsyncJob(location, "COMPLETED", "FAILED");
	}

	private void waitForAsyncJob(URI location, String completed, String failed) throws ServiceException {
		int anHour = 1_000 * 60 * 60;
		Date start = new Date();
		Date timeoutDate = new Date(start.getTime() + anHour);
		while (new Date().before(timeoutDate)) {
			try {
				ResponseEntity<StatusHolder> statusResponse = restTemplate.getForEntity(location, StatusHolder.class);
				StatusHolder statusHolder = statusResponse.getBody();
				if (completed.equals(statusHolder.getStatus())) {
					return;
				} else if (failed.equals(statusHolder.getStatus())) {
					throw new ServiceException(format("Async job failed: %s. URL: %s", statusHolder.getMessage(), location));
				}
				//noinspection BusyWait
				Thread.sleep(3_000);
			} catch (InterruptedException e) {
				throw new ServiceException(format("Thread interrupted while waiting for async job to complete. URL: %s", location));
			}
		}
		throw new ServiceException(format("Timed out while waiting for async job. URL: %s", location));
	}

	private static final class ConceptBulkLoadRequest {

		private final Set<String> conceptIds;

		public ConceptBulkLoadRequest(Collection<Long> conceptIds) {
			this.conceptIds = conceptIds.stream().map(Objects::toString).collect(Collectors.toSet());
		}

		public Set<String> getConceptIds() {
			return conceptIds;
		}
	}

	private static final class StatusHolder {

		private String status;
		private String message;

		public String getStatus() {
			return status;
		}

		public String getMessage() {
			return message;
		}
	}
}
