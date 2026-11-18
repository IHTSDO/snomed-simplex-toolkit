package org.snomed.simplex.client;
import java.net.URI;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.snomed.simplex.client.SnowstormClient.ConceptIdStream;
import static org.snomed.simplex.client.SnowstormClient.REFSET_DATA;

import org.apache.logging.log4j.util.Strings;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.RuntimeServiceException;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.snomed.simplex.client.domain.Description.Type.FSN;
import static org.snomed.simplex.client.SnowstormClientSupport.getServiceException;
import static org.snomed.simplex.client.SnowstormClientSupport.requireBody;
import static org.snomed.simplex.client.SnowstormClientSupport.conceptsCollectionPath;
import static org.snomed.simplex.client.SnowstormClient.MAX_PAGE_SIZE;
import static org.snomed.simplex.client.SnowstormClient.CONCEPTS_SEARCH_ENDPOINT;
import static org.snomed.simplex.client.SnowstormClient.SEARCH_AFTER;
import static org.snomed.simplex.client.SnowstormClient.QUERY_PARAM_MODULE;
import static org.snomed.simplex.client.SnowstormClient.QUERY_PARAM_LIMIT;
import static org.snomed.simplex.client.SnowstormClient.QUERY_PARAM_RETURN_ID_ONLY;

class SnowstormConceptSearchClient {

	private final ParameterizedTypeReference<Page<ConceptMini>> responseTypeConceptMiniPage = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<String>> responseTypeConceptIdPage = new ParameterizedTypeReference<>(){};

	private final RestTemplate restTemplate;
	private final String rootUri;
	private final int maxFetches;

	SnowstormConceptSearchClient(RestTemplate restTemplate, String rootUri, int maxFetches) {
		this.restTemplate = restTemplate;
		this.rootUri = rootUri;
		this.maxFetches = maxFetches;
	}

	public List<ConceptMini> findAllConceptsByModule(CodeSystem codeSystem, String module) throws ServiceException {
		List<ConceptMini> completeList = new ArrayList<>();
		String searchAfter = null;
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(maxFetches);
		List<ConceptMini> pageItems;
		do {
			guard.beforeFetch(searchAfter);
			Page<ConceptMini> page = findConceptsByModulePage(codeSystem, module, searchAfter, MAX_PAGE_SIZE);
			pageItems = page.getItems() != null ? page.getItems() : List.of();
			completeList.addAll(pageItems);
			if (pageItems.size() < MAX_PAGE_SIZE) {
				break;
			}
			guard.afterFullPage(searchAfter, page.getSearchAfter());
			searchAfter = page.getSearchAfter();
		} while (!Strings.isBlank(searchAfter));
		return completeList;
	}

	public Page<ConceptMini> findConceptsByModule(CodeSystem codeSystem, String module, int offset, int limit) throws ServiceExceptionWithStatusCode {
		try {
			String url = UriComponentsBuilder.fromPath(conceptsCollectionPath(codeSystem))
					.queryParam(QUERY_PARAM_MODULE, module)
					.queryParam("offset", offset)
					.queryParam(QUERY_PARAM_LIMIT, limit)
					.toUriString();
			ResponseEntity<Page<ConceptMini>> exchange = restTemplate.exchange(
					url, HttpMethod.GET, null, responseTypeConceptMiniPage);
			return exchange.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts by module");
		}
	}

	private Page<ConceptMini> findConceptsByModulePage(CodeSystem codeSystem, String module, String searchAfter, int limit)
			throws ServiceExceptionWithStatusCode {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath(conceptsCollectionPath(codeSystem))
				.queryParam(QUERY_PARAM_MODULE, module)
				.queryParam(QUERY_PARAM_LIMIT, limit);
		appendConceptSearchPagination(builder, searchAfter);
		return fetchConceptMiniPage(builder);
	}

	public Set<String> collectConceptIdsInModule(CodeSystem codeSystem, String moduleId) throws ServiceException {
		return collectAllConceptIds(() -> UriComponentsBuilder.fromPath(conceptsCollectionPath(codeSystem))
				.queryParam(QUERY_PARAM_MODULE, moduleId)
				.queryParam(QUERY_PARAM_RETURN_ID_ONLY, true));
	}

	public Set<String> collectConceptIdsWithUsPreferredActiveEnFsn(CodeSystem codeSystem, String conceptModuleId)
			throws ServiceException {
		return collectAllConceptIds(() -> UriComponentsBuilder.fromPath(conceptsCollectionPath(codeSystem))
				.queryParam(QUERY_PARAM_MODULE, conceptModuleId)
				.queryParam(QUERY_PARAM_RETURN_ID_ONLY, true)
				.queryParam("termActive", true)
				.queryParam("descriptionType", FSN.getConceptId())
				.queryParam("language", "en"));
	}

	public Supplier<ConceptMini> getConceptStream(String branch, String ecl) {
		return new ConceptMiniStream(branch, ecl);
	}

	private final class ConceptMiniStream implements Supplier<ConceptMini> {

		private final String branch;
		private final String ecl;
		private final SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(maxFetches);
		private final ParameterizedTypeReference<Page<ConceptMini>> listOfConceptMinisType = new ParameterizedTypeReference<>() {};

		private List<ConceptMini> items;
		private int itemOffset = 0;
		private String searchAfter = "";
		private boolean lastPage;

		private ConceptMiniStream(String branch, String ecl) {
			this.branch = branch;
			this.ecl = ecl;
		}

		@Override
		public ConceptMini get() {
			if ((items == null || itemOffset == items.size()) && !loadNextPage()) {
				return null;
			}
			return items.get(itemOffset++);
		}

		/**
		 * @return true if a page with items was loaded.
		 */
		private boolean loadNextPage() {
			if (lastPage) {
				return false;
			}
			beforeFetch();
			Map<String, Object> searchRequest = new HashMap<>();
			searchRequest.put("form", "inferred");
			searchRequest.put("eclFilter", ecl);
			searchRequest.put(QUERY_PARAM_LIMIT, MAX_PAGE_SIZE);
			searchRequest.put(SEARCH_AFTER, searchAfter);
			searchRequest.put("includeLeafFlag", true);

			ResponseEntity<Page<ConceptMini>> pageResponse = restTemplate.exchange(format(CONCEPTS_SEARCH_ENDPOINT, branch),
					HttpMethod.POST, new HttpEntity<>(searchRequest), listOfConceptMinisType);
			Page<ConceptMini> page = pageResponse.getBody();
			if (page == null) {
				return false;
			}
			items = page.getItems();
			itemOffset = 0;
			if (items.isEmpty()) {
				return false;
			}
			if (items.size() < MAX_PAGE_SIZE) {
				lastPage = true;
			} else {
				afterFullPage(page.getSearchAfter());
				searchAfter = page.getSearchAfter();
			}
			return true;
		}

		private void beforeFetch() {
			try {
				guard.beforeFetch(searchAfter);
			} catch (ServiceException e) {
				throw new RuntimeServiceException(e.getMessage(), e);
			}
		}

		private void afterFullPage(String nextSearchAfter) {
			try {
				guard.afterFullPage(searchAfter, nextSearchAfter);
			} catch (ServiceException e) {
				throw new RuntimeServiceException(e.getMessage(), e);
			}
		}
	}

	public Set<Long> collectActiveFsnConceptIds(CodeSystem codeSystem, String moduleId, String languageCode)
			throws ServiceException {

		String normalizedLanguage = normalizeLanguageCode(languageCode);
		LinkedHashSet<Long> conceptIds = new LinkedHashSet<>();
		for (String conceptId : collectAllConceptIds(() -> {
			UriComponentsBuilder builder = UriComponentsBuilder.fromPath(conceptsCollectionPath(codeSystem))
					.queryParam(QUERY_PARAM_MODULE, moduleId)
					.queryParam(QUERY_PARAM_RETURN_ID_ONLY, true)
					.queryParam("activeFilter", true)
					.queryParam("termActive", true)
					.queryParam("descriptionType", FSN.getConceptId());
			if (normalizedLanguage != null) {
				builder.queryParam("language", normalizedLanguage);
			}
			return builder;
		})) {
			conceptIds.add(Long.parseLong(conceptId));
		}
		return conceptIds;
	}

	private Set<String> collectAllConceptIds(Supplier<UriComponentsBuilder> queryFactory) throws ServiceException {
		LinkedHashSet<String> conceptIds = new LinkedHashSet<>();
		String searchAfter = null;
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(maxFetches);
		List<String> pageItems;
		do {
			guard.beforeFetch(searchAfter);
			Page<String> page = fetchConceptIdPage(queryFactory, searchAfter, MAX_PAGE_SIZE);
			pageItems = page.getItems() != null ? page.getItems() : List.of();
			conceptIds.addAll(pageItems);
			if (pageItems.size() < MAX_PAGE_SIZE) {
				break;
			}
			guard.afterFullPage(searchAfter, page.getSearchAfter());
			searchAfter = page.getSearchAfter();
		} while (!Strings.isBlank(searchAfter));
		return conceptIds;
	}

	private Page<String> fetchConceptIdPage(Supplier<UriComponentsBuilder> queryFactory, String searchAfter, int limit)
			throws ServiceExceptionWithStatusCode {

		UriComponentsBuilder builder = queryFactory.get();
		builder.queryParam(QUERY_PARAM_LIMIT, limit);
		appendConceptSearchPagination(builder, searchAfter);
		try {
			ResponseEntity<Page<String>> response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, responseTypeConceptIdPage);
			return requireBody(response.getBody(), "concept id search");
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concept ids");
		}
	}

	private Page<ConceptMini> fetchConceptMiniPage(UriComponentsBuilder builder) throws ServiceExceptionWithStatusCode {
		try {
			ResponseEntity<Page<ConceptMini>> response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, responseTypeConceptMiniPage);
			return requireBody(response.getBody(), "concept search");
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts");
		}
	}

	private static void appendConceptSearchPagination(UriComponentsBuilder builder, String searchAfter) {
		if (!Strings.isBlank(searchAfter)) {
			builder.queryParam(SEARCH_AFTER, searchAfter);
		} else {
			builder.queryParam("offset", 0);
		}
	}

	private static String normalizeLanguageCode(String languageCode) {
		if (languageCode == null || languageCode.isBlank()) {
			return null;
		}
		return languageCode.trim().toLowerCase();
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
		URI url = buildUriWithEcl(
				UriComponentsBuilder.fromPath(format("/browser/%s/members", codeSystem.getWorkingBranchPath())).queryParam("active", true),
				"referenceSet", refsetEcl);
		try {
			ResponseEntity<RefsetAggregationPage> response = restTemplate.exchange(url, HttpMethod.GET, null, RefsetAggregationPage.class);
			RefsetAggregationPage body = requireBody(response.getBody(), REFSET_DATA);
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

	/**
	 * Returns true if {@code ancestorConceptId} appears among ancestors of {@code conceptId} on the edition branch.
	 * Uses ECL {@code >conceptId} via concept search (returnIdOnly) and stops at the first matching ancestor.
	 */
	public boolean isAncestorOf(String ancestorConceptId, String conceptId, CodeSystem codeSystem) {
		if (ancestorConceptId == null || conceptId == null || ancestorConceptId.isBlank() || conceptId.isBlank()) {
			return false;
		}
		String ecl = ">" + conceptId;
		ConceptIdStream stream = new ConceptIdStream(codeSystem.getWorkingBranchPath(), ecl, restTemplate, maxFetches);
		String id;
		while ((id = stream.get()) != null) {
			if (ancestorConceptId.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter) throws ServiceExceptionWithStatusCode {
		return getConcepts(ecl, codeSystem, moduleFilter, 10_000);
	}

	public Page<ConceptMini> getConcepts(String ecl, CodeSystem codeSystem, String moduleFilter, int limit) throws ServiceExceptionWithStatusCode {
		try {
			UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromPath(conceptsCollectionPath(codeSystem))
					.queryParam(QUERY_PARAM_LIMIT, limit);
			if (moduleFilter != null) {
				urlBuilder.queryParam(QUERY_PARAM_MODULE, moduleFilter);
			}
			URI url = buildUriWithEcl(urlBuilder, "ecl", ecl);
			ResponseEntity<Page<ConceptMini>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseTypeConceptMiniPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch concepts");
		}
	}

	public Map<String, ConceptMini> getCodeSystemRefsetsWithTypeInformation(CodeSystem codeSystem, boolean filterByModule) throws ServiceExceptionWithStatusCode {
		String url = format("/browser/%s/members?limit=1&module=%s", codeSystem.getBranchPath(), filterByModule ? codeSystem.getDefaultModule() : "");
		ResponseEntity<RefsetAggregationPage> refsetAggregationResponse = restTemplate.getForEntity(url, RefsetAggregationPage.class);
		RefsetAggregationPage body = requireBody(refsetAggregationResponse.getBody(), "refset type data");
		return body.getReferenceSets();
	}

	public List<Long> getConceptChangeReport(String branchPath, int changedSince) throws ServiceExceptionWithStatusCode {
		ParameterizedTypeReference<Page<String>> listOfConceptIds = new ParameterizedTypeReference<>() {};
		ResponseEntity<Page<String>> response = restTemplate.exchange("/%s/concepts/change-report?changedSince=%s".formatted(branchPath, changedSince), HttpMethod.GET, null,
			listOfConceptIds);
		Page<String> body = response.getBody();
		if (body == null) {
			throw new ServiceExceptionWithStatusCode("Concept change report failed.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return body.getItems().stream().map(Long::parseLong).toList();
	}

	/**
	 * Builds a request URI with an ECL expression as a query parameter.
	 * <p>
	 * The ECL is expanded as a URI variable, which encodes strictly: spaces, braces ({@code {{ +HISTORY }}}),
	 * {@code +}, {@code ^}, {@code <} and {@code >} are all percent-encoded, so Snowstorm decodes exactly the ECL we sent.
	 * Always send the result to RestTemplate as a {@link URI}; a String would be template-expanded and encoded a second time.
	 * Use this for every GET that carries ECL.
	 * <p>
	 * RestTemplate only applies its root URI to String URLs, so the Snowstorm root URI is prepended here.
	 */
	private URI buildUriWithEcl(UriComponentsBuilder builder, String eclParamName, String ecl) {
		return SnowstormClientSupport.buildUriWithEcl(rootUri, builder, eclParamName, ecl);
	}
}
