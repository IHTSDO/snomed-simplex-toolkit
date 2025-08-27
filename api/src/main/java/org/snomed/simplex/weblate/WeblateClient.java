package org.snomed.simplex.weblate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.weblate.domain.*;
import org.snomed.simplex.weblate.pojo.BulkAddLabelRequest;
import org.snomed.simplex.weblate.pojo.WeblateAddLanguageRequest;
import org.snomed.simplex.weblate.pojo.WeblateAddLanguageRequestPlural;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WeblateClient {
	public static final String COMMON_PROJECT = "common";
	public static final String SNOMEDCT_COMPONENT = "snomedct";
	public static final String UNITS_URL = "/units/%s/";
	private static final Logger logger = LoggerFactory.getLogger(WeblateClient.class);
	private final RestTemplate restTemplate;
	private final SupportRegister supportRegister;

	public static final ParameterizedTypeReference<WeblateComponent> SET_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<WeblatePage<WeblateUnit>> UNITS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<WeblatePage<WeblateLabel>> LABELS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<Map<String, Object>> PARAMETERIZED_TYPE_REFERENCE_MAP = new ParameterizedTypeReference<>() {};

	protected WeblateClient(RestTemplate restTemplate, SupportRegister supportRegister) {
		this.restTemplate = restTemplate;
		this.supportRegister = supportRegister;
	}

	public List<WeblateComponent> listComponents(String projectSlug) {
		ParameterizedTypeReference<WeblateResponse<WeblateComponent>> responseType = new ParameterizedTypeReference<>() {};
		ResponseEntity<WeblateResponse<WeblateComponent>> response = restTemplate.exchange("/projects/%s/components/?format=json".formatted(projectSlug), HttpMethod.GET, null, responseType);
		WeblateResponse<WeblateComponent> weblateResponse = response.getBody();
		if (weblateResponse == null) {
			return new ArrayList<>();
		}
		return weblateResponse.getResults();
	}

	public WeblateComponent getComponent(String projectSlug, String componentSlug) {
		try {
			ResponseEntity<WeblateComponent> response = restTemplate.exchange("/projects/%s/components/%s".formatted(projectSlug, componentSlug),
					HttpMethod.GET, null, SET_RESPONSE_TYPE);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			return null;
		}
	}

	public void createComponent(String projectSlug, WeblateComponent weblateComponent, String gitRepo, String gitBranch) throws ServiceExceptionWithStatusCode {
		String componentSlug = weblateComponent.slug();

		// Create Weblate component based on English file in Git repo.
		Map<String, String> map = new HashMap<>();
		map.put("name", weblateComponent.name());
		map.put("slug", componentSlug);
		map.put("project", projectSlug);
		map.put("vcs", "git");
		map.put("repo", gitRepo);
		map.put("push", gitRepo);
		map.put("branch", gitBranch);
		map.put("filemask", "%s/*.csv".formatted(componentSlug));
		map.put("new_base", "%s/en.csv".formatted(componentSlug));
		map.put("template", "%s/en.csv".formatted(componentSlug));
		map.put("file_format", "csv-multi-utf-8");
		map.put("disable_autoshare", "true");
		try {
			restTemplate.exchange("/projects/%s/components/".formatted(projectSlug), HttpMethod.POST, new HttpEntity<>(map, getJsonHeaders()), Void.class);
		} catch (HttpClientErrorException e) {
			handleSharedCodeSystemError("Failed to create translation component. %s".formatted(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	public WeblatePage<WeblateUnit> getUnitPage(UnitQueryBuilder builder) {
		return doGetUnitPage(getUnitQuery(builder));
	}

	/**
	 * Get units with changes since a specific date using the optimized endpoint.
	 * This is much faster than the standard units endpoint for counting changes.
	 *
	 * @param projectSlug The project slug (e.g., "common")
	 * @param componentSlug The component slug (e.g., "snomedct")
	 * @param languageCode The language code with refset ID (e.g., "nl-58888888102")
	 * @param sinceDate The date to check changes since
	 * @param label Optional label filter
	 * @param state Optional state filter (e.g., "translated", "fuzzy")
	 * @param pageSize Page size for pagination (default 1 for counting)
	 * @return WeblatePage containing the units with changes
	 */
	public WeblatePage<WeblateUnit> getUnitsWithChangesSince(String projectSlug, String componentSlug,
			String languageCode, Date sinceDate, String label, String state, int pageSize) {

		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/translations/{project}/{component}/{language}/units_with_changes_since/")
				.queryParam("since", formatDateForWeblate(sinceDate))
				.queryParam("format", "json")
				.queryParam("page_size", pageSize);

		if (label != null && !label.isEmpty()) {
			builder.queryParam("label", label);
		}

		if (state != null && !state.isEmpty()) {
			builder.queryParam("state", state);
		}

		String finalUrl = builder.buildAndExpand(projectSlug, componentSlug, languageCode).toUriString();
		logger.info("Getting units with changes since from Weblate: {}", finalUrl);

		return restTemplate.exchange(finalUrl, HttpMethod.GET, null, UNITS_RESPONSE_TYPE).getBody();
	}

	/**
	 * Format a Date object for Weblate API consumption (ISO 8601 format).
	 */
	private String formatDateForWeblate(Date date) {
		// Use ISO 8601 format that Weblate expects
		java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		return date.toInstant().atZone(java.time.ZoneOffset.UTC).format(formatter);
	}

	private @Nullable WeblatePage<WeblateUnit> doGetUnitPage(String url) {
		logger.info("Getting unit page from Weblate: {}", url);
		return restTemplate.exchange(url, HttpMethod.GET, null, UNITS_RESPONSE_TYPE).getBody();
	}

	public UnitSupplier getUnitStream(String projectSlug, String componentSlug, int startPage) {
		return new WeblateUnitStream(projectSlug, componentSlug, startPage, this);
	}

	protected static @NotNull String getUnitQuery(UnitQueryBuilder builder) {
		return builder.build();
	}

	protected void handleSharedCodeSystemError(String message, HttpStatus httpStatus, HttpClientErrorException e) throws ServiceExceptionWithStatusCode {
		supportRegister.handleSystemError(CodeSystem.SHARED, message, new ServiceException(message, e));
		throw new ServiceExceptionWithStatusCode(message, httpStatus);
	}

	public WeblateProject getProject(String slug) {
		ParameterizedTypeReference<WeblateProject> responseType = new ParameterizedTypeReference<>() {};
		ResponseEntity<WeblateProject> response = restTemplate.exchange("/projects/%s/".formatted(slug), HttpMethod.GET, null, responseType);
		return response.getBody();
	}

	public List<WeblateProject> listProjects() {
		ParameterizedTypeReference<WeblateResponse<WeblateProject>> responseType = new ParameterizedTypeReference<>() {};
		ResponseEntity<WeblateResponse<WeblateProject>> response = restTemplate.exchange("/projects/", HttpMethod.GET, null, responseType);
		WeblateResponse<WeblateProject> weblateResponse = response.getBody();
		if (weblateResponse == null) {
			return new ArrayList<>();
		}
		return weblateResponse.getResults();
	}

	public WeblateUnit createUnit(WeblateUnit weblateUnit, String project, String component, String language) {
		// Docs: https://docs.weblate.org/en/latest/api.html#post--api-translations-(string-project)-(string-component)-(string-language)-units-
		String url = "/translations/%s/%s/%s/units/".formatted(project, component, language);
		ResponseEntity<WeblateUnit> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(weblateUnit, getJsonHeaders()), WeblateUnit.class);
		return response.getBody();
	}

	public void patchUnitExplanation(String id, String explanation) {
		// Docs: https://docs.weblate.org/en/latest/api.html#patch--api-units-(int-id)-
		Map<String, String> patchBody = new HashMap<>();
		patchBody.put("explanation", explanation);
		restTemplate.exchange(UNITS_URL.formatted(id), HttpMethod.PATCH, new HttpEntity<>(patchBody, getJsonHeaders()), Void.class);
	}

	public void patchUnitLabels(String id, List<WeblateLabel> labels) {
		// Docs: https://docs.weblate.org/en/latest/api.html#patch--api-units-(int-id)-
		Map<String, Object> patchBody = new HashMap<>();
		patchBody.put("labels", labels.stream().map(WeblateLabel::id).toList());
		restTemplate.exchange(UNITS_URL.formatted(id), HttpMethod.PATCH, new HttpEntity<>(patchBody, getJsonHeaders()), Void.class);
	}

	public void bulkAddLabels(String projectSlug, Integer labelId, List<String> contextIds) throws ServiceExceptionWithStatusCode {
		// This is a custom Weblate endpoint
		String url = "/units/bulk_add_label/";
		try {
			BulkAddLabelRequest request = new BulkAddLabelRequest(projectSlug, labelId, contextIds);
			restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, getJsonHeaders()), Void.class);
		} catch (HttpClientErrorException e) {
			throw new ServiceExceptionWithStatusCode("Error bulk assigning label: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public void deleteComponent(String project, String slug) {
		restTemplate.delete("/components/%s/%s/".formatted(project, slug));
	}

	/**
	 * Creates a new screenshot in Weblate for a specific unit.
	 *
	 * @param projectSlug The project slug
	 * @param componentSlug The component slug
	 * @param unitId The unit ID (checksum) to associate with the screenshot
	 * @param imageFile The image file to upload
	 * @return The created screenshot data if successful, null otherwise
	 * @throws ServiceException if there's an error during the process
	 */
	public Map<String, Object> uploadAndAssociateScreenshot(String conceptId, String projectSlug, String componentSlug,
			String unitId, File imageFile) throws ServiceException {

		try {
			// First, get the unit details to ensure we have the correct numeric ID
			ResponseEntity<Map<String, Object>> unitResponse = restTemplate.exchange(
				UNITS_URL.formatted(unitId),
				HttpMethod.GET,
				null,
					PARAMETERIZED_TYPE_REFERENCE_MAP
			);

			if (unitResponse.getStatusCode() != HttpStatus.OK || unitResponse.getBody() == null) {
				throw new ServiceException("Failed to get unit details");
			}

			Integer numericUnitId = (Integer) unitResponse.getBody().get("id");
			if (numericUnitId == null) {
				throw new ServiceException("Could not get numeric unit ID");
			}

			// Create multipart request for screenshot upload
			MultipartBodyBuilder builder = new MultipartBodyBuilder();
			builder.part("name", "concept_%s".formatted(conceptId));
			builder.part("project_slug", projectSlug);
			builder.part("component_slug", componentSlug);
			builder.part("language_code", "en");
			builder.part("image", new FileSystemResource(imageFile), MediaType.IMAGE_PNG);

			// Create the screenshot
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
					"/screenshots/",
					HttpMethod.POST,
					new HttpEntity<>(builder.build(), getFormHeaders()),
					PARAMETERIZED_TYPE_REFERENCE_MAP
			);

			if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
				throw new ServiceException("Failed to upload screenshot");
			}

			Map<String, Object> screenshot = response.getBody();
			if (screenshot == null) {
				throw new ServiceException("No response body received");
			}

			// Associate the unit
			String screenshotUrl = (String) screenshot.get("url");
			String screenshotId = screenshotUrl.substring(screenshotUrl.lastIndexOf("screenshots/") + 12).replace("/", "");

			builder = new MultipartBodyBuilder();
			builder.part("unit_id", numericUnitId);

			ResponseEntity<Map<String, Object>> associateResponse = restTemplate.exchange(
					"/screenshots/%s/units/".formatted(screenshotId),
					HttpMethod.POST,
					new HttpEntity<>(builder.build(), getFormHeaders()),
					PARAMETERIZED_TYPE_REFERENCE_MAP
			);

			if (associateResponse.getStatusCode() != HttpStatus.OK && associateResponse.getStatusCode() != HttpStatus.CREATED) {
				throw new ServiceException("Failed to associate unit with screenshot");
			}

			screenshot = associateResponse.getBody();

			return screenshot;

		} catch (HttpClientErrorException e) {
			throw new ServiceException("Error creating screenshot: " + e.getMessage(), e);
		}
	}

	/**
	 * Find a Weblate unit by its concept ID.
	 *
	 * @param projectSlug The project slug
	 * @param componentSlug The component slug
	 * @param conceptId The concept ID to search for
	 * @return The WeblateUnit if found, null otherwise
	 */
	public WeblateUnit getUnitForConceptId(String projectSlug, String componentSlug, String conceptId) {
		try {
			String url = UriComponentsBuilder.fromPath("/units/")
					.queryParam("project", projectSlug)
					.queryParam("component", componentSlug)
					.queryParam("q", "context:=%s and language:=en".formatted(conceptId))
					.queryParam("format", "json")
					.build()
					.toUriString();

			ResponseEntity<WeblatePage<WeblateUnit>> response = restTemplate.exchange(
				url,
				HttpMethod.GET,
				new HttpEntity<>(getJsonHeaders()),
				UNITS_RESPONSE_TYPE
			);

			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				List<WeblateUnit> results = response.getBody().results();
				if (!results.isEmpty()) {
					return results.get(0);
				}
			}
			logger.warn("No unit found for concept ID {} in project {} component {}", conceptId, projectSlug, componentSlug);
			return null;
		} catch (Exception e) {
			logger.error("Error finding unit for concept ID {}: {}", conceptId, e.getMessage(), e);
			return null;
		}
	}

	public boolean isLanguageExists(String languageCode) {
		try {
			restTemplate.getForEntity("/languages/%s/".formatted(languageCode), String.class);
			return true;
		} catch (HttpClientErrorException.NotFound e) {
			return false;
		}
	}

	public void createLanguage(String languageCodeWithRefset, String languageName, String direction) throws ServiceExceptionWithStatusCode {
		try {
			// Step 2: Prepare Form Data
			WeblateAddLanguageRequest addLanguageRequest = new WeblateAddLanguageRequest(languageCodeWithRefset, languageName,
					direction, 0, new WeblateAddLanguageRequestPlural(2, "n != 1"));

			// Step 4: Execute the Request
			restTemplate.exchange("/languages/", HttpMethod.POST, new HttpEntity<>(addLanguageRequest, getJsonHeaders()), String.class);

		} catch (HttpClientErrorException e) {
			throw new ServiceExceptionWithStatusCode(("Failed to create new language. " +
					"Snowlate status code:%s").formatted(e.getStatusCode().value()), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	public boolean isTranslationExistsSearchByLanguageRefset(String languageCodeWithRefsetId) {
		try {
			restTemplate.getForEntity("/translations/common/snomedct/%s/".formatted(languageCodeWithRefsetId), String.class);
			return true;
		} catch (HttpClientErrorException.NotFound e) {
			return false;
		}
	}

	public void createTranslation(String languageCode) {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("language_code", languageCode);
		restTemplate.postForEntity("/components/common/snomedct/translations/", new HttpEntity<>(requestBody, getJsonHeaders()), String.class);
	}

	public Map<String, Object> getLanguageStatistics(String languageCode) {
		try {
			String url = "/languages/%s/statistics/?format=json".formatted(languageCode);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
					url,
					HttpMethod.GET,
					null,
					PARAMETERIZED_TYPE_REFERENCE_MAP
			);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			return Collections.emptyMap();
		}
	}

	public WeblateLabel getCreateLabel(String project, String label, String description) {
		try {
			WeblateLabel existing = getLabel(project, label);
			if (existing != null) return existing;

			WeblateLabel newLabel = new WeblateLabel(null, label, description, "blue");
			String url = getLabelsUrl(project);
			restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(newLabel, getJsonHeaders()), Void.class);
			return getLabel(project, label);
		} catch (HttpClientErrorException e) {
			return null;
		}
	}

	public WeblateLabel getLabel(String project, String label) {
		String url = getLabelsUrl(project);
		ResponseEntity<WeblatePage<WeblateLabel>> response = restTemplate.exchange(
			url,
			HttpMethod.GET,
			null,
			LABELS_RESPONSE_TYPE
		);
		WeblatePage<WeblateLabel> page = response.getBody();
		if (page != null) {
			Optional<WeblateLabel> existing = page.results().stream().filter(result -> result.name().equals(label)).findFirst();
			return existing.orElse(null);
		} else {
			return null;
		}
	}

	private static @NotNull String getLabelsUrl(String project) {
		return "/projects/%s/labels/?format=json".formatted(project);
	}

	public void deleteLabelAsync(String project, String label) {
		// Run the deletion in background without error handling
		CompletableFuture.runAsync(() -> {
			try {
				WeblateLabel weblateLabel = getLabel(project, label);
				if (weblateLabel != null) {
					String url = "/projects/%s/labels/%s/".formatted(project, weblateLabel.id());
					restTemplate.delete(url);
				}
			} catch (Exception e) {
				// Fire and forget - no error handling
				logger.debug("Background label deletion failed for project {} label {}: {}", project, label, e.getMessage());
			}
		});
	}

	public File downloadTranslationSubset(WeblateTranslationSet translationSet) throws IOException {
		String languageCodeWithRefsetId = translationSet.getLanguageCodeWithRefsetId();
		String compositeLabel = translationSet.getCompositeLabel();

		String url = "/translations/%s/%s/%s/file/?format=csv-multi&q=label:%s+AND+state:>=translated&fields=context,target"
			.formatted(COMMON_PROJECT, SNOMEDCT_COMPONENT, languageCodeWithRefsetId, compositeLabel);

		ResponseEntity<Resource> response = restTemplate.getForEntity(url, Resource.class);

		File tempFile = File.createTempFile("translation_%s".formatted(compositeLabel), ".csv");
		Resource body = response.getBody();
		if (body != null) {
			try (FileOutputStream outputStream = new FileOutputStream(tempFile);
				 InputStream inputStream = body.getInputStream()) {
				StreamUtils.copy(inputStream, outputStream);
			}
		}
		return tempFile;
	}

	protected RestTemplate getRestTemplate() {
		return restTemplate;
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private HttpHeaders getFormHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}
}
