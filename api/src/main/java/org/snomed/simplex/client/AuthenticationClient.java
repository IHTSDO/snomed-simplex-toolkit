package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AuthenticationClient {

	private final RestTemplate restTemplate;
	public static final String TEST_IMS_URL = "TEST";
	private final boolean dummyResponse;

	public AuthenticationClient(@Value("${ims-security.api-url}") String imsUrl) {
		MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
		jsonConverter.setObjectMapper(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
		dummyResponse = imsUrl.equals(TEST_IMS_URL);
		restTemplate = new RestTemplateBuilder()
				.rootUri(imsUrl)
				.defaultHeader("Content-Type", "application/json")
				.messageConverters(List.of(jsonConverter, new AllEncompassingFormHttpMessageConverter()))
				.build();
	}

	public String fetchAuthenticationToken(String username, String password) throws ServiceException {
		if (dummyResponse) {
			return "DUMMY";
		}
		Map<String, String> requestBody = Map.of("login", username, "password", password);
		ResponseEntity<Void> response = restTemplate.exchange("/authenticate", HttpMethod.POST, new HttpEntity<>(requestBody), Void.class);
		List<String> strings = response.getHeaders().get("Set-Cookie");
		if (strings == null || strings.isEmpty()) {
			throw new ServiceException("Failed to authenticate, no auth cookie returned.");
		}
		String cookie = strings.get(0);
		return cookie.substring(0, cookie.indexOf(";"));
	}

	public UserDetails fetchUserDetails(String authenticationToken) {
		if (dummyResponse) {
			return new UserDetails("test", "Test", "User", "test@snomed.org", "Test User", true);
		}
		HttpHeaders headers = new HttpHeaders();
		headers.add("Cookie", authenticationToken);
		ResponseEntity<UserDetails> response = restTemplate.exchange("/account", HttpMethod.GET, new HttpEntity<>(headers), UserDetails.class);
		return response.getBody();
	}

	public record UserDetails(String login, String firstName, String lastName, String email, String displayName, boolean active) {
	}

}
