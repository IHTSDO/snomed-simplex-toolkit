package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.HTTPClientException;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.snomed.simplex.service.StreamUtils;
import org.snomed.simplex.util.CollectionUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.simplex.client.domain.Description.Acceptability.PREFERRED;
import static org.snomed.simplex.client.domain.Description.CaseSignificance.CASE_INSENSITIVE;
import static org.snomed.simplex.client.domain.Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
import static org.snomed.simplex.client.domain.Description.Type.FSN;
import static org.snomed.simplex.client.domain.Description.Type.SYNONYM;

public class SnowstormClient {

	public static final int MAX_PAGE_SIZE = 10_000;
	private final ParameterizedTypeReference<Page<RefsetMember>> responseTypeRefsetPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<CodeSystem>> responseTypeCodeSystemPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<ConceptMini>> responseTypeConceptMiniPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<Long>> responseTypeSCTIDPage = new ParameterizedTypeReference<>(){};

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final Map<String, String> workingBranches;

	private static final Logger logger = LoggerFactory.getLogger(SnowstormClient.class);

	public SnowstormClient(String snowstormUrl, String authenticationToken, ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.workingBranches = new HashMap<>();

		if (Strings.isBlank(snowstormUrl)) {
			throw new IllegalStateException("Snowstorm URL is not yet configured");
		}
		restTemplate = new RestTemplateBuilder()
				.rootUri(snowstormUrl)
				.defaultHeader("Cookie", authenticationToken)
				// Set the request content type to JSON
				.messageConverters(new MappingJackson2HttpMessageConverter())
				.build();
	}

	public List<CodeSystem> getCodeSystems(boolean includeDetails) throws ServiceException {
		try {
			ResponseEntity<Page<CodeSystem>> response = restTemplate.exchange("/codesystems", HttpMethod.GET, null, responseTypeCodeSystemPage);
			List<CodeSystem> items = new ArrayList<>();
			for (CodeSystem codeSystem : response.getBody().getItems()) {
				if (!codeSystem.isPostcoordinated()) {
					if (includeDetails) {
						addCodeSystemBranchInfo(codeSystem);
					}
					items.add(codeSystem);
				}
			}
			return items;
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
			ResponseEntity<ConceptMini> response = restTemplate.getForEntity(format("/%s/concepts/%s", codeSystem.getWorkingBranchPath(), conceptId), ConceptMini.class);
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
			addCodeSystemBranchInfo(codeSystem);
			return codeSystem;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load code system");
		}
	}

	private void addCodeSystemBranchInfo(CodeSystem codeSystem) throws ServiceException {
		String branchPath = codeSystem.getBranchPath();
		Branch branch = getBranchOrThrow(branchPath);
		codeSystem.setBranchObject(branch);
		String defaultModule = branch.getDefaultModule();
		codeSystem.setContentHeadTimestamp(branch.getHeadTimestamp());
		codeSystem.setDefaultModule(defaultModule);
		if (defaultModule != null) {
			String pt = getPT(codeSystem, defaultModule).orElse(null);
			codeSystem.setDefaultModuleDisplay(pt);
		}
		codeSystem.setSimplexWorkingBranch(workingBranches.get(codeSystem.getShortName()));
		codeSystem.setNamespace(branch.getMetadataValue(Branch.DEFAULT_NAMESPACE_METADATA_KEY));
        codeSystem.setClassified("true".equals(branch.getMetadataValue(Branch.CLASSIFIED_METADATA_KEY)));
		codeSystem.setShowCustomConcepts("true".equals(branch.getMetadataValue(Branch.SHOW_CUSTOM_CONCEPTS)));
		codeSystem.setDependencyPackage(branch.getMetadataValue(Branch.DEPENDENCY_PACKAGE_METADATA_KEY));
		codeSystem.setLatestValidationReport(branch.getMetadataValue(Branch.LATEST_VALIDATION_REPORT_METADATA_KEY));
		codeSystem.setPreparingRelease("true".equalsIgnoreCase(branch.getMetadataValue(Branch.PREPARING_RELEASE_METADATA_KEY)));
	}

	public Branch getBranchOrThrow(String branchPath) throws ServiceException {
		ResponseEntity<Branch> branchResponse = restTemplate.getForEntity(format("/branches/%s", branchPath), Branch.class);
		Branch branch = branchResponse.getBody();
		if (branch == null) {
			throw new ServiceException(format("Branch not found %s", branchPath));
		}
		return branch;
	}

	public void setCodeSystemWorkingBranch(CodeSystem codeSystem, String workingBranch) {
		if (!Strings.isBlank(workingBranch)) {
			if (!workingBranch.startsWith(codeSystem.getBranchPath())) {
				logger.info("Failed to set working branch path '{}', not within codesystem {}.", workingBranch, codeSystem.getBranchPath());
				workingBranch = null;
			} else if (!isBranchExists(workingBranch)) {
				logger.info("Failed to set working branch '{}', branch does not exist.", workingBranch);
				workingBranch = null;
			}
		}
		workingBranches.put(codeSystem.getShortName(), workingBranch);
	}

	private boolean isBranchExists(String branchPath) {
		try {
			restTemplate.exchange(format("/branches/%s", branchPath), HttpMethod.GET, null, Map.class);
		} catch (HttpStatusCodeException e) {
			return false;
		}
		return true;
	}

	public CodeSystem createCodeSystem(String name, String shortName, String namespace,
			CodeSystem dependantCodeSystem, Integer dependantCodeSystemVersion) throws HTTPClientException {

		String branchPath = String.format("%s/%s", dependantCodeSystem.getBranchPath(), shortName);
		try {
			CodeSystem codeSystemCreateRequest = new CodeSystem(name, shortName, branchPath);
			if (dependantCodeSystemVersion != null) {
				codeSystemCreateRequest.setDependantVersionEffectiveTime(dependantCodeSystemVersion);
			}
			restTemplate.exchange("/codesystems", HttpMethod.POST, new HttpEntity<>(codeSystemCreateRequest.setDailyBuildAvailable(true)), CodeSystem.class);
			CodeSystem codeSystem = restTemplate.getForEntity(format("/codesystems/%s", shortName), CodeSystem.class).getBody();

			// Set namespace
			upsertBranchMetadata(branchPath, Map.of(Branch.DEFAULT_NAMESPACE_METADATA_KEY, namespace));

			return codeSystem;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "create code system");
		}
	}

	public void updateCodeSystem(CodeSystem codeSystem) {
		restTemplate.exchange(format("/codesystems/%s", codeSystem.getShortName()), HttpMethod.PUT, new HttpEntity<>(codeSystem), CodeSystem.class);
	}

	public void deleteCodeSystem(String shortName) {
		restTemplate.delete(format("/codesystems/%s", shortName));
	}

	public void upsertBranchMetadata(String branchPath, Map<String, String> newBranchMetadata) {
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
		Map<String, ConceptMini> refsetConceptMap = getConcepts(refsetEcl, codeSystem, codeSystem.getDefaultModuleOrThrow()).getItems().stream()
				.peek(conceptMini -> conceptMini.setActiveMemberCount(0L))
				.collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity()));

		// Join active refset counts
		String url = format("/browser/%s/members?active=true&referenceSet=%s", codeSystem.getWorkingBranchPath(), refsetEcl);
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

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter) throws ServiceException {
		try {
			String url = format("/%s/concepts?ecl=%s%s&limit=10000", codeSystem.getWorkingBranchPath(), ecl,
					moduleFilter != null ? String.format("&module=%s", moduleFilter) : "");
			ResponseEntity<Page<ConceptMini>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseTypeConceptMiniPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts");
		}
	}

	public Page<RefsetMember> getRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly, int offset, int limit) throws HTTPClientException {
		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&offset=%s&limit=%s%s",
							codeSystem.getWorkingBranchPath(), refsetId, offset, limit, activeOnly ? "&active=true" : ""),
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
		ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&active=true&limit=1", codeSystem.getWorkingBranchPath(), refsetId),
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
			bulkJobUri = restTemplate.postForLocation(format("/%s/members/bulk", codeSystem.getWorkingBranchPath()), membersToCreateUpdate);
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
			restTemplate.exchange(format("/%s/members", codeSystem.getWorkingBranchPath()), HttpMethod.DELETE, new HttpEntity<>(bulkDeleteRequest), Void.class);
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "bulk delete refset members");
		}
	}

	public Concept createSimpleMetadataConcept(String parentConceptId, String preferredTerm, String tag, CodeSystem codeSystem) throws HTTPClientException {
		Concept concept = newSimpleMetadataConceptWithoutSave(parentConceptId, preferredTerm, tag);
		return createConcept(concept, codeSystem);
	}

	public Concept createConcept(Concept concept, CodeSystem codeSystem) throws HTTPClientException {
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
		restTemplate.delete(format("/%s/concepts/%s", codeSystem.getWorkingBranchPath(), conceptId));
	}

	public Concept updateConcept(Concept concept, CodeSystem codeSystem) throws HTTPClientException {
		try {
			ResponseEntity<Concept> response = restTemplate.exchange(format("/browser/%s/concepts/%s", codeSystem.getWorkingBranchPath(), concept.getConceptId()),
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
			ResponseEntity<Page<Long>> pageOfIds = restTemplate.exchange(format("/%s/concepts/search", codeSystem.getWorkingBranchPath()), HttpMethod.POST,
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

	private HTTPClientException getServiceException(HttpStatusCodeException e, String action) {
		return new HTTPClientException(format("Failed to %s", action), e);
	}

	public void exportRF2(OutputStream outputStream, String snowstormExportType, CodeSystem codeSystem, String transientEffectiveTime) throws ServiceException {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("branchPath", codeSystem.getWorkingBranchPath());
		requestBody.put("type", snowstormExportType);
		if (transientEffectiveTime != null) {
			requestBody.put("transientEffectiveTime", transientEffectiveTime);
		}
		URI location = restTemplate.execute("/exports", HttpMethod.POST,
				httpRequest -> {
					httpRequest.getHeaders().add("Content-Type", "application/json");
					objectMapper.writeValue(httpRequest.getBody(), requestBody);
				},
				httpResponse -> httpResponse.getHeaders().getLocation());
		if (location == null) {
			throw new ServiceException("Snowstorm did not return a location header for the RF2 export.");
		}
		String archiveLocation = location + "/archive";
		logger.info("Downloading export from {}", archiveLocation);
		restTemplate.execute(archiveLocation, HttpMethod.GET,
				httpRequest -> httpRequest.getHeaders().add("Accept", "application/zip"), httpResponse -> {
			StreamUtils.copyViaTempFile(httpResponse.getBody(), outputStream, false);
			return null;
		});
	}

	private Description.CaseSignificance guessCaseSensitivity(String name) {
		String termWithoutFirstChar = name.substring(1);
		return termWithoutFirstChar.equals(termWithoutFirstChar.toLowerCase(Locale.ROOT)) ? CASE_INSENSITIVE : ENTIRE_TERM_CASE_SENSITIVE;
	}

	public List<ConceptMini> findAllConceptsByModule(CodeSystem codeSystem, String module) {
		List<ConceptMini> completeList = new ArrayList<>();
		int offset = 0;
		int limit = 1000;
		int loadedSize;
		do {
			Page<ConceptMini> page = findConceptsByModule(codeSystem, module, offset, limit);
			List<ConceptMini> list = page.getItems();
			loadedSize = list.size();
			completeList.addAll(list);
			offset += limit;
		} while (loadedSize == 1000);
		return completeList;
	}

	public Page<ConceptMini> findConceptsByModule(CodeSystem codeSystem, String module, int offset, int limit) {
		ParameterizedTypeReference<Page<ConceptMini>> listOfConceptMinisType = new ParameterizedTypeReference<>() {};
		ResponseEntity<Page<ConceptMini>> exchange = restTemplate.exchange(
				format("/%s/concepts?module=%s&offset=%s&limit=%s", codeSystem.getWorkingBranchPath(), module, offset, limit), HttpMethod.GET, null,
				listOfConceptMinisType);
		return exchange.getBody();
	}

	public Supplier<ConceptMini> getConceptStream(String branch, String ecl) {
		return new Supplier<>() {

			private List<ConceptMini> items;
			private int itemOffset = 0;
			private String searchAfter = "";

			private final ParameterizedTypeReference<Page<ConceptMini>> listOfConceptMinisType = new ParameterizedTypeReference<>() {};

			@Override
			public ConceptMini get() {
				if (items == null || itemOffset == items.size()) {
					Map<String, Object> searchRequest = new HashMap<>();
					searchRequest.put("form", "inferred");
					searchRequest.put("eclFilter", ecl);
					searchRequest.put("limit", 10_000);
					searchRequest.put("searchAfter", searchAfter);

					String url = format("/%s/concepts/search", branch);
					ResponseEntity<Page<ConceptMini>> pageResponse = restTemplate.exchange(url,
							HttpMethod.POST, new HttpEntity<>(searchRequest), listOfConceptMinisType);
					Page<ConceptMini> page = pageResponse.getBody();
					if (page == null) {
						return null;
					}
					items = page.getItems();
					if (items.isEmpty()) {
						return null;
					}
					searchAfter = page.getSearchAfter();
					itemOffset = 0;
				}
				return items.get(itemOffset++);
			}
		};
	}

	public List<Concept> loadBrowserFormatConcepts(List<Long> conceptIds, CodeSystem codeSystem) {
		ParameterizedTypeReference<List<Concept>> listOfConcepts = new ParameterizedTypeReference<>(){};
		ResponseEntity<List<Concept>> response = restTemplate.exchange(format("/browser/%s/concepts/bulk-load", codeSystem.getWorkingBranchPath()), HttpMethod.POST,
				new HttpEntity<>(new ConceptBulkLoadRequest(conceptIds)), listOfConcepts);
		return response.getBody();
	}

	public List<Concept> loadBrowserFormatConceptsUsingGet(List<Long> conceptIds, CodeSystem codeSystem) {
		ParameterizedTypeReference<Page<Concept>> pageOfConcepts = new ParameterizedTypeReference<>(){};
		Map<String, String> params = new HashMap<>();
		params.put("conceptIds", Strings.join(conceptIds, ','));
		ResponseEntity<Page<Concept>> response = restTemplate.exchange(format("/browser/%s/concepts?conceptIds={conceptIds}", codeSystem.getWorkingBranchPath()), HttpMethod.GET,
				null, pageOfConcepts, params);
		return response.getBody().getItems();
	}

	public void createUpdateBrowserFormatConcepts(List<Concept> conceptsToUpdate, CodeSystem codeSystem) throws ServiceException {
		if (conceptsToUpdate == null || conceptsToUpdate.isEmpty()) {
			return;
		}

		List<Concept> conceptsToDelete = new ArrayList<>();
		List<Concept> conceptsForBulkUpdate = new ArrayList<>();
		for (Concept concept : conceptsToUpdate) {
			if (!concept.isActive() && !concept.isReleased()) {
				conceptsToDelete.add(concept);
			} else {
				conceptsForBulkUpdate.add(concept);
			}
		}

		String branchPath = codeSystem.getWorkingBranchPath();
		for (Concept conceptToDelete : conceptsToDelete) {
			String conceptId = conceptToDelete.getConceptId();
			logger.info("Deleting concept {} on {}", conceptId, branchPath);
			restTemplate.delete(format("/%s/concepts/%s", branchPath, conceptId));
		}

		// Start an async bulk update job
		if (!conceptsForBulkUpdate.isEmpty()) {
			logger.info("Starting bulk create/update on {}", branchPath);
			ResponseEntity<Void> response = restTemplate.exchange(format("/browser/%s/concepts/bulk", branchPath), HttpMethod.POST,
					new HttpEntity<>(conceptsForBulkUpdate), Void.class);
			URI location = response.getHeaders().getLocation();
			if (location == null) {
				throw new ServiceException("Bulk update did not return location header.");
			}
			waitForAsyncJob(location, "COMPLETED", "FAILED");
			logger.info("Completed bulk create/update on {}", branchPath);
		}
	}

	public void bulkSetDescriptionCaseSensitivity(Map<Long, Set<String>> conceptDescriptionIdMap, Description.CaseSignificance caseSensitivityWanted,
			CodeSystem codeSystem) throws ServiceException {

		List<Long> conceptIds = new ArrayList<>(conceptDescriptionIdMap.keySet());
		for (List<Long> conceptIdBatch : Lists.partition(conceptIds, 200)) {
			List<Concept> concepts = loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
			Map<Long, Concept> conceptsToSave = new HashMap<>();
			for (Concept concept : concepts) {
				for (Description description : concept.getDescriptions()) {
					Set<String> conceptDescriptionIds = conceptDescriptionIdMap.get(concept.getConceptIdAsLong());
					if (conceptDescriptionIds.contains(description.getDescriptionId())
							&& description.getCaseSignificance() != caseSensitivityWanted) {
						description.setCaseSignificance(caseSensitivityWanted);
						conceptsToSave.put(concept.getConceptIdAsLong(), concept);
					}
				}
			}
			if (!conceptsToSave.isEmpty()) {
				logger.info("Fixing case sensitivity of descriptions on {} concepts.", conceptsToSave.size());
				createUpdateBrowserFormatConcepts(new ArrayList<>(conceptsToSave.values()), codeSystem);
			}
		}
	}

	public String createClassification(String branch) throws ServiceException {
		try {
			URI location = restTemplate.postForLocation(format("/%s/classifications", branch), null);
			if (location != null) {
				String url = location.toString();
				return url.substring(url.lastIndexOf("/") + 1);
			} else {
				throw new ServiceException("Failed to create classification. API response location header is missing.");
			}
		} catch (RestClientException e) {
			throw new ServiceException("Failed to create classification. Rest client error.", e);
		}
	}

	public SnowstormClassificationJob getClassificationJob(String branch, String classificationId) throws ServiceException {
		try {
			return restTemplate.getForEntity(format("/%s/classifications/%s", branch, classificationId), SnowstormClassificationJob.class).getBody();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch classification status.");
		}
	}

	public void startClassificationSave(String branch, String classificationId) throws ServiceException {
		try {
			Map<String, String> statusChangeRequest = new HashMap<>();
			statusChangeRequest.put("status", "SAVED");
			restTemplate.exchange(format("/%s/classifications/%s", branch, classificationId), HttpMethod.PUT, new HttpEntity<>(statusChangeRequest), Void.class);
		} catch (RestClientException e) {
			throw new ServiceException("Failed to start classification save.", e);
		}
	}

	public URI createUpgradeJob(CodeSystem codeSystem, CodeSystemUpgradeRequest upgradeRequest) throws ServiceException {
		try {
			return restTemplate.postForLocation(format("/codesystems/%s/upgrade", codeSystem.getShortName()), new HttpEntity<>(upgradeRequest));
		} catch (RestClientException e) {
			throw new ServiceException("Failed to upgrade codesystem. Rest client error.", e);
		}
	}

	public SnowstormUpgradeJob getUpgradeJob(String jobLocation) throws ServiceException {
		try {
			return restTemplate.getForEntity(jobLocation, SnowstormUpgradeJob.class).getBody();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch upgrade status.");
		}
	}

	public Map<String, ConceptMini> getCodeSystemRefsetsWithTypeInformation(CodeSystem codeSystem, boolean filterByModule) {
		String url = format("/browser/%s/members?limit=1&module=%s", codeSystem.getBranchPath(), filterByModule ? codeSystem.getDefaultModule() : "");
		System.out.println(url);
		ResponseEntity<RefsetAggregationPage> refsetAggregationResponse = restTemplate.getForEntity(url, RefsetAggregationPage.class);
		return refsetAggregationResponse.getBody().getReferenceSets();
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
