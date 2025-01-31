package org.snomed.simplex.weblate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateProject;
import org.snomed.simplex.weblate.domain.WeblateSet;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class WeblateClient {

	public static final ParameterizedTypeReference<WeblateSet> SET_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<WeblatePage<WeblateUnit>> UNITS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

	private final RestTemplate restTemplate;
	private final SupportRegister supportRegister;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateClient(@Value("${weblate.url}") String url,
			@Value("${weblate.authToken}") String authToken, SupportRegister supportRegister) {

		this.supportRegister = supportRegister;

		if (authToken == null || authToken.isEmpty()) {
			logger.warn("Weblate authToken is empty");
		}

		restTemplate = new RestTemplateBuilder()
				.rootUri(url)
				.interceptors((request, body, execution) -> {
					request.getHeaders().add("Authorization", "Token " + authToken);
					request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
					return execution.execute(request, body);
				})
				.messageConverters(new MappingJackson2HttpMessageConverter())
				.build();
	}

	public List<WeblateSet> listComponents(String projectSlug) {
		ParameterizedTypeReference<WeblateResponse<WeblateSet>> responseType = new ParameterizedTypeReference<>() {};
		ResponseEntity<WeblateResponse<WeblateSet>> response = restTemplate.exchange("/projects/%s/components/".formatted(projectSlug), HttpMethod.GET, null, responseType);
		WeblateResponse<WeblateSet> weblateResponse = response.getBody();
		if (weblateResponse == null) {
			return new ArrayList<>();
		}
		return weblateResponse.getResults();
	}

	public WeblateSet getComponent(String projectSlug, String componentSlug) {
		try {
			ResponseEntity<WeblateSet> response = restTemplate.exchange("/projects/%s/components/%s".formatted(projectSlug, componentSlug),
					HttpMethod.GET, null, SET_RESPONSE_TYPE);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			return null;
		}
	}

	public void createComponent(String projectSlug, WeblateSet weblateSet, String gitRepo, String gitBranch) throws ServiceExceptionWithStatusCode {
		String componentSlug = weblateSet.slug();

		// Create Weblate component based on English file in Git repo.
		Map<String, String> map = new HashMap<>();
		map.put("name", weblateSet.name());
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
		try {
			restTemplate.exchange("/projects/%s/components/".formatted(projectSlug), HttpMethod.POST, new HttpEntity<>(map), Void.class);
		} catch (HttpClientErrorException e) {
			handleSharedCodeSystemError("Failed to create translation component. %s".formatted(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	public UnitSupplier getUnitStream(String projectSlug, String componentSlug) {
		StringBuilder query = new StringBuilder()
				.append("project").append(":").append(projectSlug)
				.append(" AND ")
				.append("component").append(":").append(componentSlug)
				.append(" AND ")
				.append("language").append(":").append("en");
		String url = "/units/?q=%s&format=json".formatted(query);

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
						ResponseEntity<WeblatePage<WeblateUnit>> response = restTemplate.exchange(url,
								HttpMethod.GET, null, UNITS_RESPONSE_TYPE);

						page = response.getBody();
						if (page != null) {
							nextUrl = page.next();
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
}
