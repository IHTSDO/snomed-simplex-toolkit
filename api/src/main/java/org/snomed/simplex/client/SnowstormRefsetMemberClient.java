package org.snomed.simplex.client;

import com.google.common.collect.Iterables;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.RuntimeServiceException;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.*;

import static java.lang.String.format;
import static org.snomed.simplex.client.SnowstormClientSupport.getServiceException;
import static org.snomed.simplex.client.SnowstormClientSupport.requireBody;
import static org.snomed.simplex.client.SnowstormClient.MAX_PAGE_SIZE;
import static org.snomed.simplex.client.SnowstormClient.REFSET_DATA;

class SnowstormRefsetMemberClient {

	private final ParameterizedTypeReference<Page<RefsetMember>> responseTypeRefsetPage = new ParameterizedTypeReference<>(){};

	private final RestTemplate restTemplate;
	private final int maxFetches;

	SnowstormRefsetMemberClient(RestTemplate restTemplate, int maxFetches) {
		this.restTemplate = restTemplate;
		this.maxFetches = maxFetches;
	}

	public Page<RefsetMember> getRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly,
			int limit, String searchAfter) throws ServiceExceptionWithStatusCode {

		try {
			ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&limit=%s%s&searchAfter=%s",
							codeSystem.getWorkingBranchPath(), refsetId, limit,
							activeOnly ? "&active=true" : "",
							searchAfter != null ? searchAfter : ""),
					HttpMethod.GET, null, responseTypeRefsetPage);
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "load refset");
		}
	}

	public List<RefsetMember> loadAllRefsetMembers(String refsetId, CodeSystem codeSystem, boolean activeOnly) throws ServiceException {
		List<RefsetMember> refsetMembers = new ArrayList<>();
		forEachRefsetMemberPage(refsetId, codeSystem, activeOnly, refsetMembers::addAll);
		return refsetMembers;
	}

	public void forEachRefsetMemberPage(String refsetId, CodeSystem codeSystem, boolean activeOnly,
			SnowstormClient.RefsetMemberPageHandler pageHandler) throws ServiceException {

		String searchAfter = null;
		Page<RefsetMember> page;
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(maxFetches);
		do {
			guard.beforeFetch(searchAfter);
			page = getRefsetMembers(refsetId, codeSystem, activeOnly, MAX_PAGE_SIZE, searchAfter);
			List<RefsetMember> items = page.getItems() != null ? page.getItems() : List.of();
			if (!items.isEmpty()) {
				pageHandler.accept(items);
			}
			if (items.size() == MAX_PAGE_SIZE) {
				guard.afterFullPage(searchAfter, page.getSearchAfter());
				searchAfter = page.getSearchAfter();
			}
		} while (page.getItems() != null && page.getItems().size() == MAX_PAGE_SIZE);
	}

	public int countAllActiveRefsetMembers(String refsetId, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(format("/%s/members?referenceSet=%s&active=true&limit=1", codeSystem.getWorkingBranchPath(), refsetId),
				HttpMethod.GET,null, responseTypeRefsetPage);
		Page<RefsetMember> page = requireBody(response.getBody(), REFSET_DATA);
		return Math.toIntExact(page.getTotal());
	}

	public int countActiveRefsetMembers(String refsetId, CodeSystem codeSystem, String moduleId) throws ServiceExceptionWithStatusCode {
		String moduleParam = moduleId != null ? format("&module=%s", moduleId) : "";
		ResponseEntity<Page<RefsetMember>> response = restTemplate.exchange(
				format("/%s/members?referenceSet=%s&active=true&limit=1%s", codeSystem.getWorkingBranchPath(), refsetId, moduleParam),
				HttpMethod.GET, null, responseTypeRefsetPage);
		Page<RefsetMember> page = requireBody(response.getBody(), REFSET_DATA);
		return Math.toIntExact(page.getTotal());
	}

	public void createUpdateRefsetMembers(List<RefsetMember> membersToCreateUpdate, CodeSystem codeSystem) throws ServiceException {
		if (membersToCreateUpdate == null || membersToCreateUpdate.isEmpty()) {
			return;
		}

		List<String> memberIds = membersToCreateUpdate.stream().map(RefsetMember::getMemberId).toList();
		Set<String> memberIdSet = new HashSet<>();
		for (String memberId : memberIds) {
			if (!memberIdSet.add(memberId)) {
				throw new IllegalArgumentException("create/update request contains a duplicate member id " + memberId);
			}
		}

		for (List<RefsetMember> batch : Iterables.partition(membersToCreateUpdate, 1_000)) {
			createUpdateRefsetMembersBatch(batch, codeSystem);
		}
	}

	private void createUpdateRefsetMembersBatch(List<RefsetMember> membersToCreateUpdate, CodeSystem codeSystem) throws ServiceException {

		URI bulkJobUri;
		try {
			bulkJobUri = restTemplate.postForLocation(format("/%s/members/bulk", codeSystem.getWorkingBranchPath()), membersToCreateUpdate);
			if (bulkJobUri == null) {
				throw new ServiceException("Failed to start bulk create/update refset member job - response location is null.");
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "start bulk create/update refset member job");
		}
		try {
			while (true) {
				ResponseEntity<SnowstormStatusHolder> response = restTemplate.getForEntity(bulkJobUri, SnowstormStatusHolder.class);
				SnowstormStatusHolder statusHolder = requireBody(response.getBody(), "Refset bulk update job");
				String status = statusHolder.getStatus();
				if ("COMPLETED".equals(status)) {
					return;
				} else if (!"RUNNING".equals(status)) {
					throw new ServiceException(format("Bulk create/update refset member job failed - %s", statusHolder.getMessage()));
				}
				Thread.sleep(1_000);// One second
			}
		} catch (HttpStatusCodeException e) {
			throw getServiceException(e, "fetch status of bulk create/update refset member job");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeServiceException("Thread interrupted while waiting for bulk create/update refset member job to complete.", e);
		}
	}

	public void deleteRefsetMembers(List<RefsetMember> membersToDelete, CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		List<String> memberIds = membersToDelete.stream().map(RefsetMember::getMemberId).toList();
		for (List<String> batch : Iterables.partition(memberIds, 1_000)) {
			Map<String, List<String>> bulkDeleteRequest = new HashMap<>();
			bulkDeleteRequest.put("memberIds", batch);
			try {
				restTemplate.exchange(format("/%s/members", codeSystem.getWorkingBranchPath()), HttpMethod.DELETE, new HttpEntity<>(bulkDeleteRequest), Void.class);
			} catch (HttpStatusCodeException e) {
				throw getServiceException(e, "bulk delete refset members");
			}
		}
	}
}
