package com.snomed.derivativemanagementtool.client;

import com.snomed.derivativemanagementtool.domain.CodeSystem;
import com.snomed.derivativemanagementtool.domain.Page;
import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class SnowstormClient {

	public static final String MAX_PAGE_SIZE = "10000";

	private final RestTemplate restTemplate;
	private final ParameterizedTypeReference<Map<String, Object>> responseTypeMap = new ParameterizedTypeReference<>(){};
	private final ParameterizedTypeReference<Page<RefsetMember>> responseTypeRefsetPage = new ParameterizedTypeReference<>(){};

	public SnowstormClient(@Value("${snowstorm.url}") String snowstormUrl) {
		restTemplate = new RestTemplateBuilder().rootUri(snowstormUrl).build();
	}

	public void ping() throws ServiceException {
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/version", HttpMethod.GET, null, responseTypeMap);
			if (response.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> body = response.getBody();
				System.out.printf("Pinged Snowstorm successfully, version %s%n", body != null ? body.get("version") : "body is null");
			}
		} catch (HttpClientErrorException clientErrorException) {
			throw new ServiceException(String.format("Failed to connect to Snowstorm - %s - %s.", clientErrorException.getStatusCode(), clientErrorException.getMessage()));
		}
	}

	public CodeSystem getCodeSystemOrThrow(String codesystemShortName) throws ServiceException {
		try {
			ResponseEntity<CodeSystem> response = restTemplate.getForEntity(String.format("/codesystems/%s", codesystemShortName), CodeSystem.class);
			return response.getBody();
		} catch (HttpClientErrorException clientErrorException) {
			throw new ServiceException(String.format("Failed to load code system '%s'.", codesystemShortName));
		}
	}

	public List<RefsetMember> loadAllRefsetMembers(String branchPath, String refsetId) throws ServiceException {
		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(String.format("/%s/members?referenceSet=%s&limit=" + MAX_PAGE_SIZE, branchPath, refsetId),
					HttpMethod.GET,null, responseTypeRefsetPage);
			Page<RefsetMember> page = response.getBody();
			if (page.getTotal() > page.getItems().size()) {
				// TODO: Load the rest. Will need to implement scrolling beyond 10K members in Snowstorm.
				System.err.println("WARNING, only the first 10K members were loaded!");
			}
			return page.getItems();
		} catch (HttpClientErrorException clientErrorException) {
			throw new ServiceException(String.format("Failed to refset '%s' from branch '%s'.", refsetId, branchPath));
		}
	}
}
