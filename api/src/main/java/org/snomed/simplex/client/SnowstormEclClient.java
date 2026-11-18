package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

import static java.lang.String.format;

class SnowstormEclClient {

	private final RestTemplate restTemplate;

	SnowstormEclClient(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public JsonNode eclStringToModel(String eclString) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<JsonNode> response = restTemplate.exchange(
				"/util/ecl-string-to-model",
				HttpMethod.POST,
				new HttpEntity<>(eclString, headers),
				JsonNode.class);
		return response.getBody();
	}

	public Map<String, String> eclModelToString(Object eclModel) {
		sanitizeEclModelForSerialization(eclModel);
		ResponseEntity<Map<String, String>> response = restTemplate.exchange(
				"/util/ecl-model-to-string",
				HttpMethod.POST,
				new HttpEntity<>(eclModel),
				new ParameterizedTypeReference<>() {});
		return response.getBody();
	}

	private void sanitizeEclModelForSerialization(Object node) {
		if (node instanceof Map<?, ?> map) {
			map.keySet().removeIf(key -> "altIdentifier".equals(key) || "returnAllMemberFields".equals(key) || "uiId".equals(key));
			map.values().forEach(this::sanitizeEclModelForSerialization);
		} else if (node instanceof List<?> list) {
			list.forEach(this::sanitizeEclModelForSerialization);
		}
	}

	public Map<String, Object> getMrcmDomainAttributes(String branchPath, String parentIds, String acceptLanguage) {
		String url = format("/mrcm/%s/domain-attributes?proximalPrimitiveModeling=false&parentIds=%s", branchPath, parentIds);
		HttpHeaders headers = new HttpHeaders();
		if (!Strings.isBlank(acceptLanguage)) {
			headers.set(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
		return response.getBody();
	}
}
