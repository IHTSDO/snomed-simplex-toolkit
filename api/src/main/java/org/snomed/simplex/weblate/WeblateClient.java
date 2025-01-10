package org.snomed.simplex.weblate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.weblate.domain.WeblateProject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class WeblateClient {

	private final RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateClient(@Value("${weblate.url}") String url, @Value("${weblate.authToken}") String authToken) {

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
				.build();
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

}
