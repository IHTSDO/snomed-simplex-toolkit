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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeblateClient {

	public static final ParameterizedTypeReference<WeblateComponent> SET_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<WeblatePage<WeblateUnit>> UNITS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

	private final RestTemplate restTemplate;
	private final SupportRegister supportRegister;

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

	public UnitSupplier getUnitStream(String projectSlug, String componentSlug) {
		String url = getUnitQuery(projectSlug, componentSlug) + "&page=1890";

		return new UnitSupplier() {

			private WeblatePage<WeblateUnit> page;

			private String nextUrl = url;
			private Iterator<WeblateUnit> iterator;
			@Override
			public WeblateUnit get() throws ServiceExceptionWithStatusCode {

				if (iterator != null && iterator.hasNext()) {
					return iterator.next();
				}

				if (page == null || nextUrl != null) {
					try {
						LoggerFactory.getLogger(getClass()).debug("Getting next unit batch from {}", nextUrl);
						ResponseEntity<WeblatePage<WeblateUnit>> response = restTemplate.exchange(nextUrl,
								HttpMethod.GET, null, UNITS_RESPONSE_TYPE);

						page = response.getBody();
						if (page != null) {
							nextUrl = page.next();
							if (nextUrl != null) {
								String[] split = nextUrl.split("&");
								String pageNum = split[1];
								pageNum = pageNum.split("=")[1];
								int done = Integer.parseInt(pageNum) * 100;
								if (done % 1000 == 0) {
									Logger logger = LoggerFactory.getLogger(getClass());
									int percentComplete = Math.round(((float) done / page.count()) * 100);
									logger.info("Completed {}/{}, {}%", String.format("%,d", done), String.format("%,d", page.count()), percentComplete);

								}
								nextUrl = nextUrl.substring(nextUrl.indexOf("/units/"));
								nextUrl = URLDecoder.decode(nextUrl, StandardCharsets.UTF_8);
							}
							iterator = page.results().iterator();
							return iterator.next();
						}
					} catch (HttpClientErrorException e) {
						handleSharedCodeSystemError("Failed to fetch translation units.", HttpStatus.INTERNAL_SERVER_ERROR, e);
					}
				}

				// Nothing left
				return null;
			}

		};
	}

	private static @NotNull String getUnitQuery(String projectSlug, String componentSlug) {
		StringBuilder query = new StringBuilder()
				.append("project").append(":").append(projectSlug)
				.append(" AND ")
				.append("component").append(":").append(componentSlug)
				.append(" AND ")
				.append("language").append(":").append("en");
		return "/units/?q=%s&format=json".formatted(query);
	}

	private void handleSharedCodeSystemError(String message, HttpStatus httpStatus, HttpClientErrorException e) throws ServiceExceptionWithStatusCode {
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
}
