package com.snomed.derivativemanagementtool.client;

import com.snomed.derivativemanagementtool.domain.CodeSystem;
import com.snomed.derivativemanagementtool.domain.Page;
import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.domain.StatusHolder;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "connect to Snowstorm");
		}
	}

	public CodeSystem getCodeSystemOrThrow(String codesystemShortName) throws ServiceException {
		try {
			ResponseEntity<CodeSystem> response = restTemplate.getForEntity(String.format("/codesystems/%s", codesystemShortName), CodeSystem.class);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load code system");
		}
	}

	public List<RefsetMember> loadAllRefsetMembers(String branchPath, String refsetId) throws ServiceException {
		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(String.format("/%s/members?referenceSet=%s&limit=%s", branchPath, refsetId, MAX_PAGE_SIZE),
					HttpMethod.GET,null, responseTypeRefsetPage);
			Page<RefsetMember> page = response.getBody();
			if (page.getTotal() > page.getItems().size()) {
				// TODO: Load the rest. Will need to implement scrolling beyond 10K members in Snowstorm.
				System.err.println("WARNING, only the first 10K members were loaded!");
			}
			return page.getItems();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load refset");
		}
	}

	public void createUpdateRefsetMembers(String branchPath, List<RefsetMember> membersToCreateUpdate) throws ServiceException {
		URI bulkJobUri;
		try {
			bulkJobUri = restTemplate.postForLocation(String.format("/%s/members/bulk", branchPath), membersToCreateUpdate);
			if (bulkJobUri == null) {
				throw new ServiceException("Failed to start bulk create/update refset member job - response location is null.");
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "start bulk create/update refset member job");
		}
		try {
			while (true) {
				ResponseEntity<StatusHolder> response = restTemplate.getForEntity(bulkJobUri, StatusHolder.class);
				StatusHolder statusHolder = response.getBody();
				String status = statusHolder.getStatus();
				if ("RUNNING".equals(status)) {
					System.out.print(".");
				} else if ("COMPLETED".equals(status)) {
					return;
				} else {
					throw new ServiceException(String.format("Bulk create/update refset member job failed - %s", statusHolder.getMessage()));
				}
				Thread.sleep(1_000);// One second
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch status of bulk create/update refset member job");
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteRefsetMembers(String branchPath, List<RefsetMember> membersToDelete) throws ServiceException {
		List<String> memberIds = membersToDelete.stream().map(RefsetMember::getMemberId).collect(Collectors.toList());
		Map<String, List<String>> bulkDeleteRequest = new HashMap<>();
		bulkDeleteRequest.put("memberIds", memberIds);
		try {
			restTemplate.exchange(String.format("/%s/members", branchPath), HttpMethod.DELETE, new HttpEntity<>(bulkDeleteRequest), Void.class);
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "bulk delete refset members");
		}
	}

	private ServiceException getServiceException(HttpStatusCodeException e, String action) {
		return new ServiceException(String.format("Failed to " + action + " - %s - %s.", e.getStatusCode(), e.getMessage()));
	}
}
