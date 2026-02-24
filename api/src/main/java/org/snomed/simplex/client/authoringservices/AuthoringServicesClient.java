package org.snomed.simplex.client.authoringservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class AuthoringServicesClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthoringServicesClient.class);

	private final RestTemplate restTemplate;

	public AuthoringServicesClient(@Value("${authoring-services.url}") String authoringServicesUrl) {
		this.restTemplate = new RestTemplateBuilder()
			.rootUri(authoringServicesUrl)
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	public Set<Project> getProjects(String codeSystemShortName, Boolean projectTranslation) {
		String projectsUrl = getProjectsUrl(codeSystemShortName, projectTranslation);
		LOGGER.trace("GET {}", projectsUrl);

		try {
			ResponseEntity<Set<Project>> response = restTemplate.exchange(projectsUrl, HttpMethod.GET, getAuthCookieHttpEntity(), new ParameterizedTypeReference<>() {
			});
			return response.getBody();
		} catch (Exception e) {
			LOGGER.error("GET {} failed: {}", projectsUrl, e.getMessage(), e);
			return Collections.emptySet();
		}
	}

	public Set<Task> getTasks(String projectKey) {
		if (projectKey == null || projectKey.isBlank()) {
			return Collections.emptySet();
		}

		String tasksUrl = getTasksUrl(projectKey);
		LOGGER.trace("GET {}", tasksUrl);

		try {
			ResponseEntity<Set<Task>> response = restTemplate.exchange(tasksUrl, HttpMethod.GET, getAuthCookieHttpEntity(), new ParameterizedTypeReference<>() {
			});
			return response.getBody();
		} catch (Exception e) {
			LOGGER.error("GET {} failed: {}", tasksUrl, e.getMessage(), e);
			return Collections.emptySet();
		}
	}

	public Task createTask(String projectKey, String taskTitle) {
		return createTask(projectKey, taskTitle, null);
	}

	public Task createTask(String projectKey, String taskTitle, String assigneeUsername) {
		if (projectKey == null || projectKey.isBlank() || taskTitle == null || taskTitle.isBlank()) {
			return null;
		}

		String taskUrl = postTaskUrl(projectKey);
		LOGGER.trace("POST {}", taskUrl);

		try {
			Map<String, Object> body = new HashMap<>();
			body.put("projectKey", projectKey);
			body.put("summary", taskTitle);
			if (assigneeUsername != null) {
				body.put("assignee", Map.of("username", assigneeUsername));
			}

			ResponseEntity<Task> response = restTemplate.exchange(taskUrl, HttpMethod.POST, new HttpEntity<>(body, getAuthHttpHeader()), Task.class
			);

			if (response.getStatusCode().is2xxSuccessful()) {
				return response.getBody();
			}
		} catch (Exception e) {
			LOGGER.error("POST {} failed: {}", taskUrl, e.getMessage(), e);
		}

		return null;
	}

	private String getProjectsUrl(String codeSystemShortName, Boolean translationProject) {
		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/projects");

		if (codeSystemShortName != null && !codeSystemShortName.isBlank()) {
			uriBuilder.queryParam("codeSystemShortName", codeSystemShortName);
		}

		if (translationProject != null) {
			uriBuilder.queryParam("translationProject", translationProject);
		}

		return uriBuilder.toUriString();
	}

	private String postTaskUrl(String projectKey) {
		return "/projects/" + projectKey + "/tasks";
	}

	private String getTasksUrl(String projectKey) {
		return "/projects/" + projectKey + "/tasks";
	}

	private HttpEntity<Void> getAuthCookieHttpEntity() {
		return new HttpEntity<>(getAuthHttpHeader());
	}

	private HttpHeaders getAuthHttpHeader() {
		String authenticationToken = SecurityUtil.getAuthenticationToken();
		if (authenticationToken == null || authenticationToken.isBlank()) {
			return HttpHeaders.EMPTY;
		}

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, authenticationToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
