package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.*;

import static java.lang.String.format;
import static org.snomed.simplex.client.SnowstormClientSupport.requireBody;

class SnowstormJobClient {

	private final RestTemplate restTemplate;

	SnowstormJobClient(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public String createClassification(String branch) throws ServiceException {
		try {
			URI location = restTemplate.postForLocation(format("/%s/classifications", branch), null);
			if (location != null) {
				String url = location.toString();
				return url.substring(url.lastIndexOf("/") + 1);
			} else {
				throw new ServiceException("Failed to create classification. API response location header is missing.");
			}
		} catch (RestClientException e) {
			throw new ServiceException("Failed to create classification. Rest client error.", e);
		}
	}

	public SnowstormClassificationJob getClassificationJob(String branch, String classificationId) throws ServiceException {
		try {
			return restTemplate.getForEntity(format("/%s/classifications/%s", branch, classificationId), SnowstormClassificationJob.class).getBody();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch classification status.");
		}
	}

	public void startClassificationSave(String branch, String classificationId) throws ServiceException {
		try {
			Map<String, String> statusChangeRequest = new HashMap<>();
			statusChangeRequest.put("status", "SAVED");
			restTemplate.exchange(format("/%s/classifications/%s", branch, classificationId), HttpMethod.PUT, new HttpEntity<>(statusChangeRequest), Void.class);
		} catch (RestClientException e) {
			throw new ServiceException("Failed to start classification save.", e);
		}
	}

	public URI createUpgradeJob(CodeSystem codeSystem, CodeSystemUpgradeRequest upgradeRequest) throws ServiceException {
		try {
			return restTemplate.postForLocation(format("/codesystems/%s/upgrade", codeSystem.getShortName()), new HttpEntity<>(upgradeRequest));
		} catch (RestClientException e) {
			throw new ServiceException("Failed to upgrade codesystem. Rest client error.", e);
		}
	}

	public SnowstormUpgradeJob getUpgradeJob(String jobLocation) throws ServiceException {
		try {
			return restTemplate.getForEntity(jobLocation, SnowstormUpgradeJob.class).getBody();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch upgrade status.");
		}
	}

	void waitForAsyncJob(URI location, String completed, String failed) throws ServiceException {
		int anHour = 1_000 * 60 * 60;
		Date start = new Date();
		Date timeoutDate = new Date(start.getTime() + anHour);
		while (new Date().before(timeoutDate)) {
			try {
				ResponseEntity<SnowstormStatusHolder> statusResponse = restTemplate.getForEntity(location, SnowstormStatusHolder.class);
				SnowstormStatusHolder statusHolder = requireBody(statusResponse.getBody(), "job status");
				if (completed.equals(statusHolder.getStatus())) {
					return;
				} else if (failed.equals(statusHolder.getStatus())) {
					throw new ServiceException(format("Async job failed: %s. URL: %s", statusHolder.getMessage(), location));
				}
				//noinspection BusyWait
				Thread.sleep(3_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ServiceException(format("Thread interrupted while waiting for async job to complete. URL: %s", location));
			}
		}
		throw new ServiceException(format("Timed out while waiting for async job. URL: %s", location));
	}
}
