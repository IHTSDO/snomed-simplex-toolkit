package org.snomed.simplex.weblate;

import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.weblate.domain.WeblateUser;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class WeblateAdminClient {

	private final RestTemplate restTemplate;

	public WeblateAdminClient(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public WeblateUser getWeblateUser(String username) {
		try {
			ResponseEntity<WeblateUser> response = restTemplate.getForEntity("/users/%s/?format=json".formatted(username), WeblateUser.class);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			return null;
		}
	}

	public WeblateUser createUser(AuthenticationClient.UserDetails userDetails) {
		return restTemplate.postForObject("/users/", getRequest(userDetails), WeblateUser.class);
	}

	public WeblateUser updateDetails(AuthenticationClient.UserDetails userDetails) {
		return restTemplate.patchForObject("/users/%s/".formatted(userDetails.login()), getRequest(userDetails), WeblateUser.class);
	}

	private static HttpEntity<Map<String, Object>> getRequest(AuthenticationClient.UserDetails userDetails) {
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("username", userDetails.login());
		requestBody.put("full_name", "%s %s".formatted(userDetails.firstName(), userDetails.lastName()));
		requestBody.put("email", userDetails.email());
		requestBody.put("is_active", userDetails.active());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(requestBody, headers);
	}

}
