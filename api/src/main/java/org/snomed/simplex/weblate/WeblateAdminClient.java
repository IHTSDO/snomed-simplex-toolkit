package org.snomed.simplex.weblate;

import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.WeblateUser;
import org.snomed.simplex.weblate.pojo.WeblateAddLanguageRequest;
import org.snomed.simplex.weblate.pojo.WeblateAddLanguageRequestPlural;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
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
		return restTemplate.postForObject("/users/", getUserDetailsRequest(userDetails), WeblateUser.class);
	}

	public void updateDetails(AuthenticationClient.UserDetails userDetails) {
		restTemplate.patchForObject("/users/%s/".formatted(userDetails.login()), getUserDetailsRequest(userDetails), WeblateUser.class);
	}

	private static HttpEntity<Map<String, Object>> getUserDetailsRequest(AuthenticationClient.UserDetails userDetails) {
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("username", userDetails.login());
		requestBody.put("full_name", "%s %s".formatted(userDetails.firstName(), userDetails.lastName()));
		requestBody.put("email", userDetails.email());
		requestBody.put("is_active", userDetails.active());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(requestBody, headers);
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
				"Translation Tool status code:%s").formatted(e.getStatusCode().value()), HttpStatus.INTERNAL_SERVER_ERROR, e);
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

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}
}
