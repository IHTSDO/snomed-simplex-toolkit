package com.snomed.simpleextensiontoolkit.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snomed.simpleextensiontoolkit.client.domain.*;
import com.snomed.simpleextensiontoolkit.domain.*;
import com.snomed.simpleextensiontoolkit.exceptions.ClientException;
import com.snomed.simpleextensiontoolkit.exceptions.ServiceException;
import com.snomed.simpleextensiontoolkit.service.StreamUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class SnowstormClient {

	public static final int MAX_PAGE_SIZE = 10_000;
	private final ParameterizedTypeReference<Map<String, Object>> responseTypeMap = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<RefsetMember>> responseTypeRefsetPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<CodeSystem>> responseTypeCodeSystemPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<ConceptMini>> responseTypeConceptMiniPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<Long>> responseTypeSCTIDPage = new ParameterizedTypeReference<>(){};

	private RestTemplate restTemplate;
	private String codesystemShortname;
	private CodeSystem codeSystem;
	private String defaultModule;
	private final ObjectMapper objectMapper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnowstormClient(CodeSystemProperties config, ObjectMapper objectMapper) {
		update(config);
		this.objectMapper = objectMapper;
	}

	public void update(CodeSystemProperties codeSystemProperties) {
		if (Strings.isBlank(codeSystemProperties.getSnowstormUrl())) {
			throw new IllegalStateException("Snowstorm URL is not yet configured");
		}
		restTemplate = new RestTemplateBuilder().rootUri(codeSystemProperties.getSnowstormUrl()).build();
		logger.info("Snowstorm URL set as '{}'", codeSystemProperties.getSnowstormUrl());
		if (codeSystemProperties.getCodesystem() != null) {
			this.codesystemShortname = codeSystemProperties.getCodesystem();
		}
		if (codeSystemProperties.getDefaultModule() != null) {
			this.defaultModule = codeSystemProperties.getDefaultModule();
		}
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
		fetchCodeSystem();
	}

	private void fetchCodeSystem() throws ServiceException {
		if (codesystemShortname == null) {
			codeSystem = null;
		} else if (codeSystem == null || !codesystemShortname.equals(codeSystem.getShortName())) {
			codeSystem = getCodeSystemOrThrow(codesystemShortname);
		}
	}

	public Page<CodeSystem> getCodeSystems() throws ServiceException {
		try {
			ResponseEntity<Page<CodeSystem>> response = restTemplate.exchange("/codesystems", HttpMethod.GET, null, responseTypeCodeSystemPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "list code systems");
		}
	}

	public CodeSystem getCodeSystemOrThrow(String codesystemShortName) throws ServiceException {
		try {
			ResponseEntity<CodeSystem> response = restTemplate.getForEntity(format("/codesystems/%s", codesystemShortName), CodeSystem.class);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load code system");
		}
	}

	public ConceptMini getRefsetOrThrow(String refsetId) throws ClientException {
		ConceptMini refset = getRefset(refsetId);
		if (refset == null) {
			throw new IllegalArgumentException("Refset not found.");
		}
		return refset;
	}

	public ConceptMini getRefset(String refsetId) throws ClientException {
		List<ConceptMini> refsets = getRefsets(refsetId);
		if (!refsets.isEmpty()) {
			return refsets.get(0);
		}
		return null;
	}

	public List<ConceptMini> getRefsets(String refsetEcl) throws ClientException {
		Map<String, ConceptMini> refsetConceptMap = getConcepts(refsetEcl, defaultModule).getItems().stream()
				.peek(conceptMini -> conceptMini.setActiveMemberCount(0L))
				.collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity()));

		// Join active refset counts
		String url = format("/browser/%s/members?active=true&module=%s&referenceSet=%s", getBranch(), defaultModule, refsetEcl);
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

	private Page<ConceptMini> getConcepts(String ecl, String moduleId) throws ClientException {
		try {
			String url = format("/%s/concepts?ecl=%s&limit=300&module=%s", getBranch(), ecl, moduleId != null ? moduleId : "");
			ResponseEntity<Page<ConceptMini>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseTypeConceptMiniPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts");
		}
	}

	public List<RefsetMember> loadAllRefsetMembers(String refsetId) throws ServiceException {
		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&limit=%s", getBranch(), refsetId, MAX_PAGE_SIZE),
					HttpMethod.GET, null, responseTypeRefsetPage);
			Page<RefsetMember> page = response.getBody();
			if (page.getTotal() > page.getItems().size()) {
				// TODO: Load the rest. Will need to implement scrolling beyond 10K members in Snowstorm.
				System.err.println("WARNING, only the first 10K members were loaded!");
			}
			return page.getItems();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load refset");
		}
	}

	public int countAllActiveRefsetMembers(String refsetId) {
		ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&active=true&limit=1", getBranch(), refsetId),
				HttpMethod.GET,null, responseTypeRefsetPage);
		Page<RefsetMember> page = response.getBody();
		return Math.toIntExact(page.getTotal());
	}

	public void createUpdateRefsetMembers(List<RefsetMember> membersToCreateUpdate) throws ServiceException {

		List<String> memberIds = membersToCreateUpdate.stream().map(RefsetMember::getMemberId).collect(Collectors.toList());
		Set<String> memberIdSet = new HashSet<>();
		for (String memberId : memberIds) {
			if (!memberIdSet.add(memberId)) {
				throw new IllegalArgumentException("create/update request contains a duplicate member id " + memberId);
			}
		}

		URI bulkJobUri;
		try {
			bulkJobUri = restTemplate.postForLocation(format("/%s/members/bulk", getBranch()), membersToCreateUpdate);
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

	public void deleteRefsetMembers(List<RefsetMember> membersToDelete) throws ServiceException {
		List<String> memberIds = membersToDelete.stream().map(RefsetMember::getMemberId).collect(Collectors.toList());
		Map<String, List<String>> bulkDeleteRequest = new HashMap<>();
		bulkDeleteRequest.put("memberIds", memberIds);
		try {
			restTemplate.exchange(format("/%s/members", getBranch()), HttpMethod.DELETE, new HttpEntity<>(bulkDeleteRequest), Void.class);
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "bulk delete refset members");
		}
	}

	public Concept createSimpleMetadataConcept(String parentConceptId, String preferredTerm, String tag) throws ClientException {
		String caseSens = guessCaseSensitivity(preferredTerm);
		Concept concept = new Concept(defaultModule)
				.addDescription(new Description(Concepts.FSN, "en", format("%s (%s)", preferredTerm, tag), caseSens, Concepts.US_LANG_REFSET, "PREFERRED"))
				.addDescription(new Description(Concepts.SYNONYM, "en", preferredTerm, caseSens, Concepts.US_LANG_REFSET, "PREFERRED"))
				.addAxiom(new Axiom("PRIMITIVE", Collections.singletonList(Relationship.stated(Concepts.IS_A, parentConceptId))))
				.addRelationship(Relationship.inferred(Concepts.IS_A, parentConceptId));
		try {
			ResponseEntity<Concept> response = restTemplate.exchange(format("/browser/%s/concepts", getBranch()), HttpMethod.POST, new HttpEntity<>(concept), Concept.class);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "create concept");
		}
	}

	public List<Long> getConceptIds(Collection<String> conceptIds) throws ServiceException {
		if (conceptIds.isEmpty()) {
			return new ArrayList<>();
		}

		ConceptSearchRequest searchRequest = new ConceptSearchRequest(conceptIds);
		searchRequest.setReturnIdOnly(true);
		searchRequest.setActiveFilter(true);
		searchRequest.setLimit(MAX_PAGE_SIZE);
		try {
			ResponseEntity<Page<Long>> pageOfIds = restTemplate.exchange(format("/%s/concepts/search", getBranch()), HttpMethod.POST,
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

	public CodeSystem getCodeSystem() {
		return codeSystem;
	}

	private ClientException getServiceException(HttpStatusCodeException e, String action) {
		return new ClientException(format("Failed to %s", action), e);
	}

	public String getBranch() {
		if (codeSystem == null) {
			throw new IllegalStateException("Code System is not yet set.");
		}
		if (codeSystem.getBranchPath() == null) {
			throw new IllegalStateException("Code System branch path is not yet set.");
		}
		return codeSystem.getBranchPath();
	}

	public void exportRF2(OutputStream outputStream, String snowstormExportType) throws ServiceException {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("branchPath", getBranch());
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

	private String guessCaseSensitivity(String name) {
		String termWithoutFirstChar = name.substring(1);
		return termWithoutFirstChar.equals(termWithoutFirstChar.toLowerCase(Locale.ROOT)) ? "CASE_INSENSITIVE" : "ENTIRE_TERM_CASE_SENSITIVE";
	}
}
