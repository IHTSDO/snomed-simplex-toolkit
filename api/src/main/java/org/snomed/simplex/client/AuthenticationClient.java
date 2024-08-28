package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
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

	public AuthenticationClient(@Value("${ims-security.api-url}") String imsUrl) {
		MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
		jsonConverter.setObjectMapper(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
		restTemplate = new RestTemplateBuilder()
				.rootUri(imsUrl)
				.defaultHeader("Content-Type", "application/json")
				.messageConverters(List.of(jsonConverter, new AllEncompassingFormHttpMessageConverter()))
				.build();
	}

	public String fetchAuthenticationToken(String username, String password) throws ServiceException {
		Map<String, String> requestBody = Map.of("login", username, "password", password);
		ResponseEntity<Void> response = restTemplate.exchange("/authenticate", HttpMethod.POST, new HttpEntity<>(requestBody), Void.class);
		List<String> strings = response.getHeaders().get("Set-Cookie");
		if (strings == null || strings.isEmpty()) {
			throw new ServiceException("Failed to authenticate, no auth cookie returned.");
		}
		String cookie = strings.get(0);
		return cookie.substring(0, cookie.indexOf(";"));
	}

}
