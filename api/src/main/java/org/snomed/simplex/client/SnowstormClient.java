package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.RuntimeServiceException;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.snomed.simplex.client.SnowstormClientSupport.getServiceException;
import static org.snomed.simplex.client.SnowstormClientSupport.requireBody;

public class SnowstormClient {

	public static final int MAX_PAGE_SIZE = 10_000;
	public static final String CONCEPT_ENDPOINT = "/%s/concepts/%s";
	public static final String CONCEPTS_COLLECTION_ENDPOINT = "/%s/concepts";
	public static final String CONCEPTS_SEARCH_ENDPOINT = "/%s/concepts/search";
	public static final String SEARCH_AFTER = "searchAfter";
	public static final String QUERY_PARAM_MODULE = "module";
	public static final String QUERY_PARAM_LIMIT = "limit";
	public static final String QUERY_PARAM_RETURN_ID_ONLY = "returnIdOnly";
	public static final String CODESYSTEMS_ENDPOINT = "/codesystems";
	public static final String CODESYSTEM_ENDPOINT = "/codesystems/%s";
	public static final String CODESYSTEMS_VERSIONS_ENDPOINT = "/codesystems/%s/versions";
	public static final String BRANCH_X_ENDPOINT = "/branches/%s";
	public static final String ROOT_CODESYSTEM = "SNOMEDCT";
	public static final String REFSET_DATA = "refset data";

	/** Read timeout for POST /exports and GET …/exports/{id}/archive (large ZIP stream). Other Snowstorm calls use {@link #restTemplate} defaults. */
	private static final Duration RF2_EXPORT_READ_TIMEOUT = Duration.ofMinutes(10);

	/**
	 * Cache TTL for CodeSystem objects: 10 minutes
	 */
	private static final long CACHE_TTL_MS = Duration.ofMinutes(10).toMillis();

	private final ParameterizedTypeReference<Page<CodeSystem>> responseTypeCodeSystemPage = new ParameterizedTypeReference<>(){};

	private final RestTemplate restTemplate;
	private final String rootUri;
	private final SnowstormRefsetMemberClient refsetMemberClient;
	private final SnowstormConceptSearchClient conceptSearchClient;
	private final SnowstormJobClient jobClient;
	private final SnowstormEclClient eclClient;
	private final SnowstormConceptClient conceptClient;
	private final SnowstormExportClient exportClient;
	private final Map<String, String> workingBranches;
	private final Map<String, CachedCodeSystem> codeSystemCache;
	private final DependantEditionLookup dependantEditionLookup;
	private final int maxFetches;

	private static final Logger logger = LoggerFactory.getLogger(SnowstormClient.class);

	public SnowstormClient(String snowstormUrl, String authenticationToken, String userAgent, ObjectMapper objectMapper, int maxFetches) {
		this.maxFetches = maxFetches;
		this.workingBranches = new HashMap<>();
		this.codeSystemCache = new ConcurrentHashMap<>();
		this.dependantEditionLookup = new DependantEditionLookup();

		if (isBlank(snowstormUrl)) {
			throw new IllegalStateException("Snowstorm URL is not yet configured");
		}
		this.rootUri = snowstormUrl;
		RestTemplateBuilder builder = new RestTemplateBuilder()
				.rootUri(snowstormUrl)
				.messageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter());
		if (!isBlank(authenticationToken)) {
			builder = builder.defaultHeader("Cookie", authenticationToken);
		}
		if (!isBlank(userAgent)) {
			builder = builder.defaultHeader("User-Agent", userAgent);
		}
		restTemplate = builder.build();
		ClientHttpRequestFactorySettings exportHttpSettings = ClientHttpRequestFactorySettings.defaults()
				.withReadTimeout(RF2_EXPORT_READ_TIMEOUT);
		RestTemplate restTemplateRf2Export = builder
				.requestFactory(() -> ClientHttpRequestFactoryBuilder.detect().build(exportHttpSettings))
				.build();
		this.refsetMemberClient = new SnowstormRefsetMemberClient(restTemplate, maxFetches);
		this.conceptSearchClient = new SnowstormConceptSearchClient(restTemplate, rootUri, maxFetches);
		this.jobClient = new SnowstormJobClient(restTemplate);
		this.eclClient = new SnowstormEclClient(restTemplate);
		this.conceptClient = new SnowstormConceptClient(restTemplate, jobClient);
		this.exportClient = new SnowstormExportClient(restTemplateRf2Export, objectMapper);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public List<CodeSystem> getCodeSystems(boolean includeDetails) throws ServiceException {
		try {
			ResponseEntity<Page<CodeSystem>> response = restTemplate.exchange(CODESYSTEMS_ENDPOINT, HttpMethod.GET, null, responseTypeCodeSystemPage);
			List<CodeSystem> items = new ArrayList<>();
			Page<CodeSystem> body = requireBody(response.getBody(), "CodeSystem list");
			for (CodeSystem codeSystem : body.getItems()) {
				if (!codeSystem.isPostcoordinated()) {
					if (includeDetails) {
						addCodeSystemBranchInfo(codeSystem);
					}
					if (codeSystem.getName() == null) {
						logger.info("The name of CodeSystem {} must be set before it can be used.", codeSystem.getShortName());
						continue;
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
		// Check cache first
		CachedCodeSystem cached = codeSystemCache.get(codesystemShortName);
		if (cached != null && isCacheValid(cached)) {
			ensureDependantEditionInfo(cached.codeSystem);
			refreshBranchDerivedState(cached.codeSystem);
			return cached.codeSystem;
		}

		ServiceExceptionWithStatusCode notFoundException = new ServiceExceptionWithStatusCode(format("Code System not found %s", codesystemShortName), HttpStatus.NOT_FOUND);
		try {
			ResponseEntity<CodeSystem> response = restTemplate.getForEntity(format(CODESYSTEM_ENDPOINT, codesystemShortName), CodeSystem.class);
			CodeSystem codeSystem = response.getBody();
			if (codeSystem == null) {
				throw notFoundException;
			}
			addCodeSystemBranchInfo(codeSystem);
			// Store in cache
			codeSystemCache.put(codesystemShortName, new CachedCodeSystem(codeSystem, System.currentTimeMillis()));
			return codeSystem;
		} catch (HttpClientErrorException.NotFound e) {
			throw notFoundException;
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load code system");
		}
	}

	private void addCodeSystemBranchInfo(CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		Branch branch = getBranchOrThrow(codeSystem.getBranchPath());
		applyBranchDerivedState(codeSystem, branch);
		String defaultModule = branch.getDefaultModule();
		codeSystem.setDefaultModule(defaultModule);
		if (defaultModule != null) {
			String pt = getPT(codeSystem, defaultModule).orElse(null);
			codeSystem.setDefaultModuleDisplay(pt);
		}
		codeSystem.setSimplexWorkingBranch(workingBranches.get(codeSystem.getShortName()));
		populateDependantEditionInfo(codeSystem);
		clearOldMetadata(branch);
		// Update cache if exists
		codeSystemCache.put(codeSystem.getShortName(), new CachedCodeSystem(codeSystem, System.currentTimeMillis()));
	}

	private void refreshBranchDerivedState(CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		Branch branch = getBranchOrThrow(codeSystem.getBranchPath());
		applyBranchDerivedState(codeSystem, branch);
	}

	private void applyBranchDerivedState(CodeSystem codeSystem, Branch branch) {
		codeSystem.setBranchObject(branch);
		codeSystem.setContentHeadTimestamp(branch.getHeadTimestamp());
		codeSystem.setNamespace(branch.getMetadataValue(Branch.DEFAULT_NAMESPACE_METADATA_KEY));
		codeSystem.setClassified("true".equals(branch.getMetadataValue(Branch.CLASSIFIED_METADATA_KEY)));
		codeSystem.setShowCustomConcepts("true".equals(branch.getMetadataValue(Branch.SHOW_CUSTOM_CONCEPTS)));
		codeSystem.setShowUsEnglishSynonyms("true".equals(branch.getMetadataValue(Branch.SHOW_US_ENGLISH_SYNONYMS)));
		codeSystem.setShowGbEnglishSynonyms("true".equals(branch.getMetadataValue(Branch.SHOW_GB_ENGLISH_SYNONYMS)));
		codeSystem.setValidationIgnoreCase("true".equalsIgnoreCase(branch.getMetadataValue(Branch.SIMPLEX_VALIDATION_IGNORE_CASE_METADATA_KEY)));
		codeSystem.setConceptsMaintainedExternally("true".equalsIgnoreCase(branch.getMetadataValue(Branch.SIMPLEX_CONCEPTS_MAINTAINED_EXTERNALLY_METADATA_KEY)));
		codeSystem.setDependencyPackage(branch.getMetadataValue(Branch.DEPENDENCY_PACKAGE_METADATA_KEY));
		codeSystem.setPreviousPackage(branch.getMetadataValue(Branch.PREVIOUS_PACKAGE_METADATA_KEY));
		codeSystem.setLatestValidationReport(branch.getMetadataValue(Branch.LATEST_VALIDATION_REPORT_METADATA_KEY));
		codeSystem.setLatestReleaseCandidateBuild(branch.getMetadataValue(Branch.LATEST_BUILD_METADATA_KEY));
		codeSystem.setBuildStatus(CodeSystemBuildStatus.fromBranchMetadata(branch.getMetadataValue(Branch.BUILD_STATUS_METADATA_KEY)));
		codeSystem.setEditionStatus(getEditionStatus(branch.getMetadataValue(Branch.EDITION_STATUS_METADATA_KEY)));
		codeSystem.setTranslationLanguages(getTranslationLanguages(branch, Branch.SIMPLEX_TRANSLATION_METADATA_KEY));
		codeSystem.setTranslationSnolateLanguages(getTranslationLanguages(branch, Branch.SIMPLEX_TRANSLATION_SNOLATE_METADATA_KEY));
	}

	private void populateDependantEditionInfo(CodeSystem codeSystem) {
		String parentBranch = DependantEditionLookup.getParentBranchPath(codeSystem.getBranchPath());
		if (parentBranch == null) {
			return;
		}
		DependantEditionLookup.DependantEdition dependantEdition = dependantEditionLookup.resolve(parentBranch, this::loadDependantEdition);
		DependantEditionLookup.applyDependantEdition(codeSystem, dependantEdition);
	}

	private void ensureDependantEditionInfo(CodeSystem codeSystem) {
		if (codeSystem.getDependantEditionName() != null) {
			return;
		}
		populateDependantEditionInfo(codeSystem);
	}

	private DependantEditionLookup.DependantEdition loadDependantEdition(String parentBranch) {
		try {
			String url = format("%s?forBranch=%s", CODESYSTEMS_ENDPOINT, parentBranch);
			ResponseEntity<Page<CodeSystem>> response = restTemplate.exchange(
					url,
					HttpMethod.GET,
					null,
					responseTypeCodeSystemPage);
			Page<CodeSystem> body = response.getBody();
			if (body == null || body.getItems() == null || body.getItems().isEmpty()) {
				return null;
			}
			CodeSystem parentCodeSystem = body.getItems().get(0);
			return new DependantEditionLookup.DependantEdition(parentCodeSystem.getName(), parentCodeSystem.getShortName());
		} catch (HttpStatusCodeException e) {
			logger.warn("Failed to load dependant edition for branch {}", parentBranch, e);
			return null;
		}
	}

	private void clearOldMetadata(Branch branch) {
		Map<String, Object> metadata = branch.getMetadata();
		boolean change = false;
		if (metadata.get(Branch.OLD_KEY_PREVIOUS_DEPENDENCY_PACKAGE_METADATA_KEY) != null) {
			metadata.remove(Branch.OLD_KEY_PREVIOUS_DEPENDENCY_PACKAGE_METADATA_KEY);
			change = true;
		}
		if (change) {
			logger.info("Clearing old code system metadata for {}", branch.getPath());
			saveAllBranchMetadata(branch.getPath(), metadata);
		}
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
		if (isBlank(editionStatus) || !EditionStatus.getNames().contains(editionStatus)) {
			editionStatus = EditionStatus.AUTHORING.name();
		}
		return EditionStatus.valueOf(editionStatus);
	}

	public void rollbackCommitsGreaterThan(String branchPath, Long commitToKeep) throws ServiceExceptionWithStatusCode {
		logger.info("Rolling back commits on {} until {}", branchPath, commitToKeep);
		Long headTimestamp = getBranchOrThrow(branchPath).getHeadTimestamp();
		while (headTimestamp > commitToKeep) {
			rollbackCommit(branchPath, headTimestamp);
			headTimestamp = getBranchOrThrow(branchPath).getHeadTimestamp();
		}
		logger.info("Rollback on {} complete. Head now at {}.", branchPath, headTimestamp);
	}

	private void rollbackCommit(String branchPath, Long headTimestamp) {
		String url = "/admin/%s/actions/rollback-commit?commitHeadTime=%s".formatted(branchPath, headTimestamp);
		logger.info("Rolling back commit {} on {}", headTimestamp, branchPath);
		restTemplate.exchange(url, HttpMethod.POST, null, Void.class);
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
		if (!isBlank(workingBranch)) {
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
			restTemplate.exchange(CODESYSTEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(codeSystemCreateRequest.setDailyBuildAvailable(true)), CodeSystem.class);
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
		// Invalidate cache after update
		codeSystemCache.remove(codeSystem.getShortName());
	}

	public void versionCodeSystem(CodeSystem codeSystem, String effectiveTime) {
		CodeSystemVersioningRequest request = new CodeSystemVersioningRequest(effectiveTime, "Release %s".formatted(effectiveTime), false);
		restTemplate.exchange(format(CODESYSTEMS_VERSIONS_ENDPOINT, codeSystem.getShortName()), HttpMethod.POST, new HttpEntity<>(request), Void.class);
	}

	public void startNewAuthoringCycle(CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		try {
			restTemplate.postForEntity(format("%s/new-authoring-cycle", format(CODESYSTEM_ENDPOINT, codeSystem.getShortName())), null, Void.class);
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "start new authoring cycle");
		}
	}

	public void deleteCodeSystem(String shortName) {
		restTemplate.delete(format(CODESYSTEM_ENDPOINT, shortName));
		// Invalidate cache after deletion
		codeSystemCache.remove(shortName);
	}

	public void invalidateCodeSystemCache(String codeSystemShortName) {
		codeSystemCache.remove(codeSystemShortName);
		dependantEditionLookup.clear();
	}

	public void invalidateAllCodeSystemCaches() {
		codeSystemCache.clear();
		dependantEditionLookup.clear();
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
		for (Branch childBranch : children.getBody() != null ? children.getBody() : List.<Branch>of()) {
			deleteBranchAndChildren(childBranch.getPath());
		}

		restTemplate.delete(format("/admin/%s/actions/hard-delete", branchPath));
	}

	public ConceptMini getRefsetOrThrow(String refsetId, CodeSystem codeSystem) throws ServiceException {
		return conceptSearchClient.getRefsetOrThrow(refsetId, codeSystem);
	}

	public ConceptMini getRefset(String refsetId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.getRefset(refsetId, codeSystem);
	}

	public List<ConceptMini> getRefsets(String refsetEcl, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.getRefsets(refsetEcl, codeSystem);
	}

	public boolean isAncestorOf(String ancestorConceptId, String conceptId, CodeSystem codeSystem) {
		return conceptSearchClient.isAncestorOf(ancestorConceptId, conceptId, codeSystem);
	}

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.getConcepts(ecl, codeSystem, moduleFilter);
	}

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter, int limit) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.getConcepts(ecl, codeSystem, moduleFilter, limit);
	}

	public Page<RefsetMember> getRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly,
			int limit, String searchAfter) throws ServiceExceptionWithStatusCode {
		return refsetMemberClient.getRefsetMembers(refsetId, codeSystem, activeOnly, limit, searchAfter);
	}

	public List<RefsetMember> loadAllRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly) throws ServiceException {
		return refsetMemberClient.loadAllRefsetMembers(refsetId, codeSystem, activeOnly);
	}

	public void forEachRefsetMemberPage(String refsetId, CodeSystem codeSystem, boolean activeOnly,
			RefsetMemberPageHandler pageHandler) throws ServiceException {
		refsetMemberClient.forEachRefsetMemberPage(refsetId, codeSystem, activeOnly, pageHandler);
	}

	public int countAllActiveRefsetMembers(String refsetId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return refsetMemberClient.countAllActiveRefsetMembers(refsetId, codeSystem);
	}

	public int countActiveRefsetMembers(String refsetId, CodeSystem codeSystem, String moduleId) throws ServiceExceptionWithStatusCode {
		return refsetMemberClient.countActiveRefsetMembers(refsetId, codeSystem, moduleId);
	}

	public void createUpdateRefsetMembers(List<RefsetMember> membersToCreateUpdate, CodeSystem codeSystem) throws ServiceException {
		refsetMemberClient.createUpdateRefsetMembers(membersToCreateUpdate, codeSystem);
	}

	public void deleteRefsetMembers(List<RefsetMember> membersToDelete, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		refsetMemberClient.deleteRefsetMembers(membersToDelete, codeSystem);
	}

	@FunctionalInterface
	public interface RefsetMemberPageHandler {
		void accept(List<RefsetMember> page) throws ServiceException;
	}

	public ConceptMini getConceptMini(String conceptId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptClient.getConceptMini(conceptId, codeSystem);
	}

	public Concept createSimpleMetadataConcept(String parentConceptId, String preferredTerm, String tag, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptClient.createSimpleMetadataConcept(parentConceptId, preferredTerm, tag, codeSystem);
	}

	public Concept createConcept(Concept concept, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptClient.createConcept(concept, codeSystem);
	}

	public Concept newSimpleMetadataConceptWithoutSave(String parentConceptId, String preferredTerm, String tag) {
		return conceptClient.newSimpleMetadataConceptWithoutSave(parentConceptId, preferredTerm, tag);
	}

	public void deleteConcept(String conceptId, CodeSystem codeSystem) {
		conceptClient.deleteConcept(conceptId, codeSystem);
	}

	public Concept updateConcept(Concept concept, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptClient.updateConcept(concept, codeSystem);
	}

	public List<Long> getConceptIds(Collection<String> conceptIds, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptClient.getConceptIds(conceptIds, codeSystem);
	}

	public enum ExportType {
		DELTA, SNAPSHOT
	}

	public void exportRF2(OutputStream outputStream, SnowstormExportConfiguration exportConfiguration) throws ServiceException {
		exportClient.exportRF2(outputStream, exportConfiguration);
	}

	public List<ConceptMini> findAllConceptsByModule(CodeSystem codeSystem, String module) throws ServiceException {
		return conceptSearchClient.findAllConceptsByModule(codeSystem, module);
	}

	public Page<ConceptMini> findConceptsByModule(CodeSystem codeSystem, String module, int offset, int limit) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.findConceptsByModule(codeSystem, module, offset, limit);
	}

	public Set<String> collectConceptIdsInModule(CodeSystem codeSystem, String moduleId) throws ServiceException {
		return conceptSearchClient.collectConceptIdsInModule(codeSystem, moduleId);
	}

	public Set<String> collectConceptIdsWithUsPreferredActiveEnFsn(CodeSystem codeSystem, String conceptModuleId)
			throws ServiceException {
		return conceptSearchClient.collectConceptIdsWithUsPreferredActiveEnFsn(codeSystem, conceptModuleId);
	}

	public Set<Long> collectActiveFsnConceptIds(CodeSystem codeSystem, String moduleId, String languageCode)
			throws ServiceException {
		return conceptSearchClient.collectActiveFsnConceptIds(codeSystem, moduleId, languageCode);
	}

	public Supplier<ConceptMini> getConceptSortedHierarchyStream(String branch, String focusConcept) {
		return new ConceptSortedHierarchyStream(branch, focusConcept, this);
	}

	public List<Long> getConceptChangeReport(String branchPath, int changedSince) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.getConceptChangeReport(branchPath, changedSince);
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
		return conceptSearchClient.getConceptStream(branch, ecl);
	}

	public ConceptIdStream getConceptIdStream(String branch, String ecl) {
		return new ConceptIdStream(branch, ecl, restTemplate, maxFetches);
	}

	public static final class ConceptIdStream implements Supplier<String> {

		private final String branch;
		private final String ecl;
		private final RestTemplate restTemplate;
		private final SearchAfterPaginationGuard guard;

		private List<String> items;
		private int itemOffset = 0;
		private String searchAfter = "";
		private boolean lastPage;
		private int total;

		private final ParameterizedTypeReference<Page<Long>> listOfConceptIdsType = new ParameterizedTypeReference<>() {};

		public ConceptIdStream(String branch, String ecl, RestTemplate restTemplate, int maxFetches) {
			this.branch = branch;
			this.ecl = ecl;
			this.restTemplate = restTemplate;
			this.guard = new SearchAfterPaginationGuard(maxFetches);
		}

		@Override
		public String get() {
			if (items == null || itemOffset == items.size()) {
				if (lastPage) {
					return null;
				}
				try {
					guard.beforeFetch(searchAfter);
				} catch (ServiceException e) {
					throw new RuntimeServiceException(e.getMessage(), e);
				}
				Map<String, Object> searchRequest = new HashMap<>();
				searchRequest.put("form", "inferred");
				searchRequest.put("eclFilter", ecl);
				searchRequest.put(QUERY_PARAM_RETURN_ID_ONLY, true);
				searchRequest.put(QUERY_PARAM_LIMIT, MAX_PAGE_SIZE);
				searchRequest.put(SEARCH_AFTER, searchAfter);

				String url = format(CONCEPTS_SEARCH_ENDPOINT, branch);
				ResponseEntity<Page<Long>> pageResponse = restTemplate.exchange(url, HttpMethod.POST,
						new HttpEntity<>(searchRequest), listOfConceptIdsType);
				Page<Long> page = pageResponse.getBody();
				if (page == null) {
					return null;
				}
				total = page.getTotal().intValue();
				items = page.getItems() == null ? List.of() : page.getItems().stream().map(String::valueOf).toList();
				itemOffset = 0;
				if (items.isEmpty()) {
					return null;
				}
				if (items.size() < MAX_PAGE_SIZE) {
					lastPage = true;
				} else {
					try {
						guard.afterFullPage(searchAfter, page.getSearchAfter());
					} catch (ServiceException e) {
						throw new RuntimeServiceException(e.getMessage(), e);
					}
					searchAfter = page.getSearchAfter();
				}
			}
			return items.get(itemOffset++);
		}

		public int getTotal() {
			return total;
		}
	}

	public List<Concept> loadBrowserFormatConcepts(List<Long> conceptIds, CodeSystem codeSystem) {
		return conceptClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
	}

	public List<Concept> loadBrowserFormatConceptsByDescriptionIds(Collection<String> descriptionIds, CodeSystem codeSystem) {
		return conceptClient.loadBrowserFormatConceptsByDescriptionIds(descriptionIds, codeSystem);
	}

	public Map<String, Boolean> loadDescriptionActiveFlags(Collection<String> descriptionIds, CodeSystem codeSystem) {
		return conceptClient.loadDescriptionActiveFlags(descriptionIds, codeSystem);
	}

	public List<Concept> loadBrowserFormatConceptsUsingGet(List<Long> conceptIds, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		return conceptClient.loadBrowserFormatConceptsUsingGet(conceptIds, codeSystem);
	}

	public void createUpdateBrowserFormatConcepts(List<Concept> conceptsToUpdate, CodeSystem codeSystem) throws ServiceException {
		conceptClient.createUpdateBrowserFormatConcepts(conceptsToUpdate, codeSystem);
	}

	public Branch setAuthorFlag(String branchPath, String name, String value) {
		if (branchPath == null || branchPath.isBlank() || name == null || name.isBlank() || value == null || value.isBlank()) {
			return null;
		}

		String authorFlagsUrl = postAuthorFlag(branchPath);
		logger.trace("POST {}", authorFlagsUrl);

		try {
			Map<String, String> body = Map.of("name", name, "value", value);
			ResponseEntity<Branch> responseEntity = restTemplate.postForEntity(authorFlagsUrl, new HttpEntity<>(body), Branch.class);
			return responseEntity.getBody();
		} catch (Exception e) {
			logger.error("POST {} failed: {}", authorFlagsUrl, e.getMessage(), e);
			return null;
		}
	}

	public void bulkChangeDescriptions(Map<Long, Set<String>> conceptDescriptionIdMap,
			CodeSystem codeSystem, Predicate<Description> processDescription, String changeForLogMessage) throws ServiceException {
		conceptClient.bulkChangeDescriptions(conceptDescriptionIdMap, codeSystem, processDescription, changeForLogMessage);
	}

	public FsnBulkRemovalStats bulkRemoveDescriptionsMatching(CodeSystem codeSystem, Collection<Long> conceptIds,
			Predicate<Description> shouldRemove, String changeForLogMessage) throws ServiceException {
		return conceptClient.bulkRemoveDescriptionsMatching(codeSystem, conceptIds, shouldRemove, changeForLogMessage);
	}

	public String createClassification(String branch) throws ServiceException {
		return jobClient.createClassification(branch);
	}

	public SnowstormClassificationJob getClassificationJob(String branch, String classificationId) throws ServiceException {
		return jobClient.getClassificationJob(branch, classificationId);
	}

	public void startClassificationSave(String branch, String classificationId) throws ServiceException {
		jobClient.startClassificationSave(branch, classificationId);
	}

	public URI createUpgradeJob(CodeSystem codeSystem, CodeSystemUpgradeRequest upgradeRequest) throws ServiceException {
		return jobClient.createUpgradeJob(codeSystem, upgradeRequest);
	}

	public SnowstormUpgradeJob getUpgradeJob(String jobLocation) throws ServiceException {
		return jobClient.getUpgradeJob(jobLocation);
	}

	public Map<String, ConceptMini> getCodeSystemRefsetsWithTypeInformation(CodeSystem codeSystem, boolean filterByModule) throws ServiceExceptionWithStatusCode {
		return conceptSearchClient.getCodeSystemRefsetsWithTypeInformation(codeSystem, filterByModule);
	}

	public void setVersionReleasePackage(CodeSystem codeSystem, String effectiveTime, String releasePackageFilename) {
		String url = format("/codesystems/%s/versions/%s?releasePackage=%s", codeSystem.getShortName(), effectiveTime, releasePackageFilename);
		restTemplate.exchange(url, HttpMethod.PUT, null, Void.class);
	}

	public void addTranslationLanguage(String refsetId, String languageCode, CodeSystem codeSystem) {
		upsertBranchMetadata(codeSystem.getBranchPath(),
				Map.of((Branch.SIMPLEX_TRANSLATION_METADATA_KEY + "%s").formatted(refsetId), languageCode));
		codeSystem.getTranslationLanguages().put(refsetId, languageCode);
	}

	public void addSnolateTranslationLanguage(String refsetId, String languageCode, CodeSystem codeSystem) {
		upsertBranchMetadata(codeSystem.getBranchPath(),
				Map.of((Branch.SIMPLEX_TRANSLATION_SNOLATE_METADATA_KEY + "%s").formatted(refsetId), languageCode));
		codeSystem.getTranslationSnolateLanguages().put(refsetId, languageCode);
	}

	private Map<String, String> getTranslationLanguages(Branch branch, String metadataKey) {
		Map<String, String> translationLanguages = new HashMap<>();
		branch.getMetadata().forEach((key, value) -> {
			if (key.startsWith(metadataKey)) {
				translationLanguages.put(key.substring(metadataKey.length()), value.toString());
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

	public void removeSnolateTranslationLanguage(String refsetId, CodeSystem codeSystem) throws ServiceException {
		String branchPath = codeSystem.getBranchPath();
		Branch branch = getBranchOrThrow(branchPath);
		Map<String, Object> metadata = branch.getMetadata();
		metadata.remove((Branch.SIMPLEX_TRANSLATION_SNOLATE_METADATA_KEY + "%s").formatted(refsetId));
		saveAllBranchMetadata(branchPath, metadata);
		if (codeSystem.getTranslationSnolateLanguages() != null) {
			codeSystem.getTranslationSnolateLanguages().remove(refsetId);
		}
	}

	public Branch createBranch(String parent, String name) {
		if (parent == null || parent.isBlank() || name == null || name.isBlank()) {
			return null;
		}

		try {
			Map<String, String> body = Map.of("parent", parent, "name", name);
			ResponseEntity<Branch> exchange = restTemplate.exchange("/branches", HttpMethod.POST, new HttpEntity<>(body), Branch.class);
			return exchange.getBody();
		} catch (Exception e) {
			logger.error("Failed to create branch", e);
			return null;
		}
	}

	private String postAuthorFlag(String branchPath) {
		return "/branches/" + branchPath + "/actions/set-author-flag";
	}

	public JsonNode eclStringToModel(String eclString) {
		return eclClient.eclStringToModel(eclString);
	}

	public Map<String, String> eclModelToString(Object eclModel) {
		return eclClient.eclModelToString(eclModel);
	}

	public Map<String, Object> getMrcmDomainAttributes(String branchPath, String parentIds, String acceptLanguage) {
		return eclClient.getMrcmDomainAttributes(branchPath, parentIds, acceptLanguage);
	}

	private boolean isCacheValid(CachedCodeSystem cached) {
		return (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS;
	}

	private record CachedCodeSystem(CodeSystem codeSystem, long timestamp) {
	}

}
