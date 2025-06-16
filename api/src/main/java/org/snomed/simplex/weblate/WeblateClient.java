package org.snomed.simplex.weblate;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.weblate.domain.WeblateComponent;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateProject;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.util.*;

public class WeblateClient {
	private static final Logger logger = LoggerFactory.getLogger(WeblateClient.class);
	private final RestTemplate restTemplate;
	private final SupportRegister supportRegister;

	public static final ParameterizedTypeReference<WeblateComponent> SET_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<WeblatePage<WeblateUnit>> UNITS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
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
			restTemplate.exchange("/projects/%s/components/".formatted(projectSlug), HttpMethod.POST, new HttpEntity<>(map), Void.class);
		} catch (HttpClientErrorException e) {
			handleSharedCodeSystemError("Failed to create translation component. %s".formatted(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}
	public WeblatePage<WeblateUnit> getUnitPage(String projectSlug, String componentSlug) {
		String url = getUnitQuery(projectSlug, componentSlug);
		ResponseEntity<WeblatePage<WeblateUnit>> response = restTemplate.exchange(url,
				HttpMethod.GET, null, UNITS_RESPONSE_TYPE);
		return response.getBody();
	}

	public UnitSupplier getUnitStream(String projectSlug, String componentSlug, int startPage) {
		return new WeblateUnitStream(projectSlug, componentSlug, startPage, this);
	}

	protected static @NotNull String getUnitQuery(String projectSlug, String componentSlug) {
		StringBuilder query = new StringBuilder()
				.append("project").append(":").append(projectSlug)
				.append(" AND ")
				.append("component").append(":").append(componentSlug)
				.append(" AND ")
				.append("language").append(":").append("en");
		return "/units/?q=%s&format=json".formatted(query);
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
		ResponseEntity<WeblateUnit> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(weblateUnit), WeblateUnit.class);
		return response.getBody();
	}

	public void patchUnitExplanation(String id, String explanation) {
		// Docs: https://docs.weblate.org/en/latest/api.html#patch--api-units-(int-id)-
		Map<String, String> patchBody = new HashMap<>();
		patchBody.put("explanation", explanation);
		restTemplate.exchange("/units/%s/".formatted(id), HttpMethod.PATCH, new HttpEntity<>(patchBody), Void.class);
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
				"/units/%s/".formatted(unitId),
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
					.queryParam("q", "context:=" + conceptId)
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
