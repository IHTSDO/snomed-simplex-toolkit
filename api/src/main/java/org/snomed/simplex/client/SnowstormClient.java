package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.snomed.simplex.service.StreamUtils;
import org.snomed.simplex.util.CollectionUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.util.function.Predicate;
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
	public static final String CONCEPT_ENDPOINT = "/%s/concepts/%s";
	public static final String CODESYSTEM_ENDPOINT = "/codesystems/%s";
	public static final String CODESYSTEMS_VERSIONS_ENDPOINT = "/codesystems/%s/versions";
	public static final String BRANCH_X_ENDPOINT = "/branches/%s";
	public static final String ROOT_CODESYSTEM = "SNOMEDCT";

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
			Page<CodeSystem> body = response.getBody();
			throwIfNull(body, "CodeSystem list");
			for (CodeSystem codeSystem : body.getItems()) {
				if (!codeSystem.isPostcoordinated()) {
					if (includeDetails) {
						addCodeSystemBranchInfo(codeSystem);
					}
					items.add(codeSystem);
				}
			}
			items.sort(Comparator.comparing(CodeSystem::getName));
			return items;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "list code systems");
		}
	}

	private static void throwIfNull(Object object, String type) throws ServiceExceptionWithStatusCode {
		if (object == null) {
			throw new ServiceExceptionWithStatusCode("Failed to fetch %s from Snowstorm.".formatted(type), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public CodeSystem getCodeSystemForDisplay(String codesystemShortName) throws ServiceException {
		CodeSystem codeSystem = getCodeSystemOrThrow(codesystemShortName);
		codeSystem.setDefaultModuleDisplay(getPT(codeSystem, codeSystem.getDefaultModuleOrThrow()).orElse("Concept not found"));
		return codeSystem;
	}

	private Optional<String> getPT(CodeSystem codeSystem, String conceptId) {
		try {
			ResponseEntity<ConceptMini> response = restTemplate.getForEntity(format(CONCEPT_ENDPOINT, codeSystem.getWorkingBranchPath(), conceptId), ConceptMini.class);
			ConceptMini conceptMini = response.getBody();
			if (conceptMini == null) {
				return Optional.empty();
			}
			return Optional.of(conceptMini.getPtOrFsnOrConceptId());
		} catch (HttpClientErrorException e) {
			return Optional.empty();
		}
	}

	public CodeSystem getCodeSystemOrThrow(String codesystemShortName) throws ServiceExceptionWithStatusCode {
		ServiceExceptionWithStatusCode notFoundException = new ServiceExceptionWithStatusCode(format("Code System not found %s", codesystemShortName), HttpStatus.NOT_FOUND);
		try {
			ResponseEntity<CodeSystem> response = restTemplate.getForEntity(format(CODESYSTEM_ENDPOINT, codesystemShortName), CodeSystem.class);
			CodeSystem codeSystem = response.getBody();
			if (codeSystem == null) {
				throw notFoundException;
			}
			addCodeSystemBranchInfo(codeSystem);
			return codeSystem;
		} catch (HttpClientErrorException.NotFound e) {
			throw notFoundException;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load code system");
		}
	}

	private void addCodeSystemBranchInfo(CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
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
		codeSystem.setPreviousPackage(branch.getMetadataValue(Branch.PREVIOUS_PACKAGE_METADATA_KEY));
		codeSystem.setPreviousDependencyPackage(branch.getMetadataValue(Branch.PREVIOUS_DEPENDENCY_PACKAGE_METADATA_KEY));
		codeSystem.setLatestValidationReport(branch.getMetadataValue(Branch.LATEST_VALIDATION_REPORT_METADATA_KEY));
		codeSystem.setLatestReleaseCandidateBuild(branch.getMetadataValue(Branch.LATEST_BUILD_METADATA_KEY));
		codeSystem.setBuildStatus(CodeSystemBuildStatus.fromBranchMetadata(branch.getMetadataValue(Branch.BUILD_STATUS_METADATA_KEY)));
		codeSystem.setEditionStatus(getEditionStatus(branch.getMetadataValue(Branch.EDITION_STATUS_METADATA_KEY)));
		codeSystem.setTranslationLanguages(getTranslationLanguages(branch));
	}

	public List<CodeSystemVersion> getVersions(CodeSystem codeSystem) {
		String url = format(CODESYSTEMS_VERSIONS_ENDPOINT + "?showFutureVersions=true", codeSystem.getShortName());
		ParameterizedTypeReference<Page<CodeSystemVersion>> responseType = new ParameterizedTypeReference<>() {};
		ResponseEntity<Page<CodeSystemVersion>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseType);
		Page<CodeSystemVersion> body = response.getBody();
		if (body != null) {
			return body.getItems();
		} else {
			return Collections.emptyList();
		}
	}

	private static EditionStatus getEditionStatus(String editionStatus) {
		if (Strings.isBlank(editionStatus) || !EditionStatus.getNames().contains(editionStatus)) {
			editionStatus = EditionStatus.AUTHORING.name();
		}
		return EditionStatus.valueOf(editionStatus);
	}

	public Branch getBranchOrThrow(String branchPath) throws ServiceExceptionWithStatusCode {
		try {
			ResponseEntity<Branch> branchResponse = restTemplate.getForEntity(format(BRANCH_X_ENDPOINT, branchPath), Branch.class);
			Branch branch = branchResponse.getBody();
			if (branch == null) {
				throw new ServiceExceptionWithStatusCode(format("Branch not found %s", branchPath), HttpStatus.NOT_FOUND);
			}
			return branch;
		} catch (HttpClientErrorException.NotFound e) {
			throw new ServiceExceptionWithStatusCode(format("Branch not found %s", branchPath), HttpStatus.NOT_FOUND);
		}
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
			restTemplate.exchange(format(BRANCH_X_ENDPOINT, branchPath), HttpMethod.GET, null, Map.class);
		} catch (HttpStatusCodeException e) {
			return false;
		}
		return true;
	}

	public CodeSystem createCodeSystem(String name, String shortName, String namespace,
			CodeSystem dependantCodeSystem, Integer dependantCodeSystemVersion) throws ServiceExceptionWithStatusCode {

		String branchPath = String.format("%s/%s", dependantCodeSystem.getBranchPath(), shortName);
		try {
			CodeSystem codeSystemCreateRequest = new CodeSystem(name, shortName, branchPath);
			if (dependantCodeSystemVersion != null) {
				codeSystemCreateRequest.setDependantVersionEffectiveTime(dependantCodeSystemVersion);
			}
			restTemplate.exchange("/codesystems", HttpMethod.POST, new HttpEntity<>(codeSystemCreateRequest.setDailyBuildAvailable(true)), CodeSystem.class);
			CodeSystem codeSystem = restTemplate.getForEntity(format(CODESYSTEM_ENDPOINT, shortName), CodeSystem.class).getBody();

			// Set namespace
			upsertBranchMetadata(branchPath, Map.of(Branch.DEFAULT_NAMESPACE_METADATA_KEY, namespace));

			return codeSystem;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "create code system");
		}
	}

	public void updateCodeSystem(CodeSystem codeSystem) {
		restTemplate.exchange(format(CODESYSTEM_ENDPOINT, codeSystem.getShortName()), HttpMethod.PUT, new HttpEntity<>(codeSystem), CodeSystem.class);
	}

	public void versionCodeSystem(CodeSystem codeSystem, String effectiveTime) {
		CodeSystemVersioningRequest request = new CodeSystemVersioningRequest(effectiveTime, "Release %s".formatted(effectiveTime), false);
		restTemplate.exchange(format(CODESYSTEMS_VERSIONS_ENDPOINT, codeSystem.getShortName()), HttpMethod.POST, new HttpEntity<>(request), Void.class);
	}

	public void deleteCodeSystem(String shortName) {
		restTemplate.delete(format(CODESYSTEM_ENDPOINT, shortName));
	}

	public void setAuthorPermissions(CodeSystem newCodeSystem, String groupName) {
		Map<String, List<String>> params = new HashMap<>();
		params.put("userGroups", List.of(groupName));
		for (String role : Set.of("AUTHOR", "ADMIN")) {
			restTemplate.exchange(format("/admin/permissions/%s/role/%s", newCodeSystem.getBranchPath(), role), HttpMethod.PUT,
					new HttpEntity<>(params), Void.class);
		}
	}

	public void upsertBranchMetadata(String branchPath, Map<String, String> newBranchMetadata) {
		restTemplate.exchange(format("/branches/%s/metadata-upsert", branchPath), HttpMethod.PUT, new HttpEntity<>(newBranchMetadata), Void.class);
	}

	public void saveAllBranchMetadata(String branchPath, Map<String, Object> allBranchMetadata) {
		Map<String, Map<String, Object>> metdataUpdateRequest = Map.of("metadata", allBranchMetadata);
		restTemplate.exchange(format(BRANCH_X_ENDPOINT, branchPath), HttpMethod.PUT, new HttpEntity<>(metdataUpdateRequest), Void.class);
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

	public ConceptMini getRefset(String refsetId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		List<ConceptMini> refsets = getRefsets(refsetId, codeSystem);
		if (!refsets.isEmpty()) {
			return refsets.get(0);
		}
		return null;
	}

	public List<ConceptMini> getRefsets(String refsetEcl, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		// Get refset concepts from code system branch and module
		Map<String, ConceptMini> refsetConceptMap = getConcepts(refsetEcl, codeSystem, codeSystem.getDefaultModuleOrThrow()).getItems().stream()
				.collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity()));
		refsetConceptMap.values().forEach(conceptMini -> conceptMini.setActiveMemberCount(0L));

		// Join active refset counts
		String url = format("/browser/%s/members?active=true&referenceSet=%s", codeSystem.getWorkingBranchPath(), refsetEcl);
		try {
			ResponseEntity<RefsetAggregationPage> response = restTemplate.exchange(url, HttpMethod.GET, null, RefsetAggregationPage.class);
			RefsetAggregationPage body = response.getBody();
			throwIfNull(body, "refset data");
			List<ConceptMini> refsetsWithActiveMemberCount = body.getRefsetsWithActiveMemberCount();
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

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter) throws ServiceExceptionWithStatusCode {
		return getConcepts(ecl, codeSystem, moduleFilter, 10_000);
	}

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter, int limit) throws ServiceExceptionWithStatusCode {
		try {
			String url = format("/%s/concepts?ecl=%s%s&limit=%s", codeSystem.getWorkingBranchPath(), ecl,
					moduleFilter != null ? String.format("&module=%s", moduleFilter) : "", limit);
			ResponseEntity<Page<ConceptMini>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseTypeConceptMiniPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts");
		}
	}

	public Page<RefsetMember> getRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly,
			int limit, String searchAfter) throws ServiceExceptionWithStatusCode {

		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&limit=%s%s&searchAfter=%s",
							codeSystem.getWorkingBranchPath(), refsetId, limit,
							activeOnly ? "&active=true" : "",
							searchAfter != null ? searchAfter : ""),
					HttpMethod.GET, null, responseTypeRefsetPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load refset");
		}
	}

	public List<RefsetMember> loadAllRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly) throws ServiceException {
		List<RefsetMember> refsetMembers = new ArrayList<>();
		String searchAfter = null;
		Page<RefsetMember> page;
		do {
			page = getRefsetMembers(refsetId, codeSystem, activeOnly, MAX_PAGE_SIZE, searchAfter);
			refsetMembers.addAll(page.getItems());
			searchAfter = page.getSearchAfter();
		} while (!page.getItems().isEmpty());

		return refsetMembers;
	}

	public int countAllActiveRefsetMembers(String refsetId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&active=true&limit=1", codeSystem.getWorkingBranchPath(), refsetId),
				HttpMethod.GET,null, responseTypeRefsetPage);
		Page<RefsetMember> page = response.getBody();
		throwIfNull(page, "refset data");
		return Math.toIntExact(page.getTotal());
	}

	public void createUpdateRefsetMembers(List<RefsetMember> membersToCreateUpdate, CodeSystem codeSystem) throws ServiceException {

		List<String> memberIds = membersToCreateUpdate.stream().map(RefsetMember::getMemberId).toList();
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
				throwIfNull(statusHolder, "Refset bulk update job");
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

	public void deleteRefsetMembers(List<RefsetMember> membersToDelete, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		List<String> memberIds = membersToDelete.stream().map(RefsetMember::getMemberId).toList();
		for (List<String> batch : Iterables.partition(memberIds, 1_000)) {
			Map<String, List<String>> bulkDeleteRequest = new HashMap<>();
			bulkDeleteRequest.put("memberIds", batch);
			try {
				restTemplate.exchange(format("/%s/members", codeSystem.getWorkingBranchPath()), HttpMethod.DELETE, new HttpEntity<>(bulkDeleteRequest), Void.class);
			} catch (HttpStatusCodeException e) {
				throw getServiceException(e, "bulk delete refset members");
			}
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

				ResponseEntity<Page<Long>> pageOfIds = restTemplate.exchange(format("/%s/concepts/search", codeSystem.getWorkingBranchPath()), HttpMethod.POST,
						new HttpEntity<>(searchRequest), responseTypeSCTIDPage);
				Page<Long> page = pageOfIds.getBody();
				throwIfNull(page, "concept search results");
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

	private ServiceExceptionWithStatusCode getServiceException(HttpStatusCodeException e, String action) {
		return new ServiceExceptionWithStatusCode(format("Failed to %s", action), HttpStatus.resolve(e.getStatusCode().value()), e);
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

	public Supplier<ConceptMini> getConceptSortedHierarchyStream(String branch, String focusConcept) {
		return new ConceptSortedHierarchyStream(branch, focusConcept, this);
	}

	protected List<ConceptMini> getConceptList(String branch, String ecl) {
		List<ConceptMini> list = new ArrayList<>();
		Supplier<ConceptMini> conceptStream = getConceptStream(branch, ecl);
		ConceptMini mini;
		while ((mini = conceptStream.get()) != null) {
			list.add(mini);
		}
		return list;
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
					itemOffset = 0;
					if (items.isEmpty()) {
						return null;
					}
					searchAfter = page.getSearchAfter();
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

	public List<Concept> loadBrowserFormatConceptsUsingGet(List<Long> conceptIds, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		ParameterizedTypeReference<Page<Concept>> pageOfConcepts = new ParameterizedTypeReference<>(){};
		Map<String, String> params = new HashMap<>();
		params.put("conceptIds", Strings.join(conceptIds, ','));
		ResponseEntity<Page<Concept>> response = restTemplate.exchange(format("/browser/%s/concepts?conceptIds={conceptIds}", codeSystem.getWorkingBranchPath()), HttpMethod.GET,
				null, pageOfConcepts, params);
		Page<Concept> body = response.getBody();
		throwIfNull(body, "concepts");
		return body.getItems();
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
				concept.setDescriptions(getNewDescriptionSet(concept));
				conceptsForBulkUpdate.add(concept);
			}
		}

		String branchPath = codeSystem.getWorkingBranchPath();
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
			waitForAsyncJob(location, "COMPLETED", "FAILED");
			logger.info("Completed bulk create/update on {}", branchPath);
		}
	}

	private void deleteConcepts(List<Concept> conceptsToDelete, String branchPath) {
		for (Concept conceptToDelete : conceptsToDelete) {
			String conceptId = conceptToDelete.getConceptId();
			logger.info("Deleting concept {} on {}", conceptId, branchPath);
			restTemplate.delete(format(CONCEPT_ENDPOINT, branchPath, conceptId));
		}
	}

	private static @NotNull List<Description> getNewDescriptionSet(Concept concept) {
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

	public void bulkChangeDescriptions(Map<Long, Set<String>> conceptDescriptionIdMap,
			CodeSystem codeSystem, Predicate<Description> processDescription, String changeForLogMessage) throws ServiceException {

		List<Long> conceptIds = new ArrayList<>(conceptDescriptionIdMap.keySet());
		for (List<Long> conceptIdBatch : Lists.partition(conceptIds, 200)) {
			List<Concept> concepts = loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
			bulkChangeDescriptionBatch(conceptDescriptionIdMap, codeSystem, processDescription, changeForLogMessage, concepts);
		}
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

	public Map<String, ConceptMini> getCodeSystemRefsetsWithTypeInformation(CodeSystem codeSystem, boolean filterByModule) throws ServiceExceptionWithStatusCode {
		String url = format("/browser/%s/members?limit=1&module=%s", codeSystem.getBranchPath(), filterByModule ? codeSystem.getDefaultModule() : "");
		ResponseEntity<RefsetAggregationPage> refsetAggregationResponse = restTemplate.getForEntity(url, RefsetAggregationPage.class);
		RefsetAggregationPage body = refsetAggregationResponse.getBody();
		throwIfNull(body, "refset type data");
		return body.getReferenceSets();
	}

	private void waitForAsyncJob(URI location, String completed, String failed) throws ServiceException {
		int anHour = 1_000 * 60 * 60;
		Date start = new Date();
		Date timeoutDate = new Date(start.getTime() + anHour);
		while (new Date().before(timeoutDate)) {
			try {
				ResponseEntity<StatusHolder> statusResponse = restTemplate.getForEntity(location, StatusHolder.class);
				StatusHolder statusHolder = statusResponse.getBody();
				throwIfNull(statusHolder, "job status");
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

	public void setVersionReleasePackage(CodeSystem codeSystem, String effectiveTime, String releasePackageFilename) {
		String url = format("/codesystems/%s/versions/%s?releasePackage=%s", codeSystem.getShortName(), effectiveTime, releasePackageFilename);
		restTemplate.exchange(url, HttpMethod.PUT, null, Void.class);
	}

	public void addTranslationLanguage(String refsetId, String languageCode, CodeSystem codeSystem, SnowstormClient snowstormClient) {
		snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(),
				Map.of((Branch.SIMPLEX_TRANSLATION_METADATA_KEY + "%s").formatted(refsetId), languageCode));
		codeSystem.getTranslationLanguages().put(refsetId, languageCode);
	}

	private Map<String, String> getTranslationLanguages(Branch branch) {
		Map<String, String> translationLanguages = new HashMap<>();
		branch.getMetadata().forEach((key, value) -> {
			if (key.startsWith(Branch.SIMPLEX_TRANSLATION_METADATA_KEY)) {
				translationLanguages.put(key.substring(Branch.SIMPLEX_TRANSLATION_METADATA_KEY.length()), value.toString());
			}
		});
		return translationLanguages;
	}

	public void removeTranslationLanguage(String refsetId, CodeSystem codeSystem) throws ServiceException {
		String branchPath = codeSystem.getBranchPath();
		Branch branch = getBranchOrThrow(branchPath);
		Map<String, Object> metadata = branch.getMetadata();
		metadata.remove((Branch.SIMPLEX_TRANSLATION_METADATA_KEY + "%s").formatted(refsetId));
		saveAllBranchMetadata(branchPath, metadata);
		codeSystem.getTranslationLanguages().remove(refsetId);
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
