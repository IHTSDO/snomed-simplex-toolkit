package org.snomed.simplex.weblate;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.*;
import org.snomed.simplex.weblate.pojo.BulkAddLabelRequest;
import org.snomed.simplex.weblate.pojo.WeblateAddGroupRequest;
import org.snomed.simplex.weblate.pojo.WeblateAddLanguageRequest;
import org.snomed.simplex.weblate.pojo.WeblateAddLanguageRequestPlural;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WeblateAdminClient {

	public static final String ADD_SUGGESTION_ROLE = "Add suggestion";
	public static final String TRANSLATE_ROLE = "Translate";
	public static final String AUTOMATIC_TRANSLATION_ROLE = "Automatic translation";
	public static final String REVIEW_STRINGS_ROLE = "Review strings";

	private static final ParameterizedTypeReference<WeblateResponse<WeblateGroup>> GROUPS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	private static final ParameterizedTypeReference<WeblateResponse<WeblateRole>> ROLES_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	private static final ParameterizedTypeReference<WeblateResponse<WeblateUserResponse>> USERS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
	public static final ParameterizedTypeReference<WeblatePage<WeblateLabel>> LABELS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

	private static final Map<Integer, WeblateGroup> userGroupCache = new ConcurrentHashMap<>();
	private static final Map<String, WeblateRole> roleCache = new ConcurrentHashMap<>();

	private final RestTemplate restTemplate;
	private final Logger logger = LoggerFactory.getLogger(WeblateAdminClient.class);

	public WeblateAdminClient(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public List<WeblateUserResponse> listUsers() {
		ResponseEntity<WeblateResponse<WeblateUserResponse>> response = restTemplate.exchange("/users/?format=json", HttpMethod.GET, null, USERS_RESPONSE_TYPE);
		WeblateResponse<WeblateUserResponse> weblateResponse = response.getBody();
		if (weblateResponse == null) {
			return new ArrayList<>();
		}
		return weblateResponse.getResults();
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

	public Set<WeblateGroup> getUserGroups(WeblateUser weblateUser) {
		Set<Integer> groupIds = weblateUser.getGroupIds();
		return groupIds.stream().map(id -> userGroupCache.computeIfAbsent(id, this::getUserGroup)).collect(Collectors.toSet());
	}

	public WeblateGroup getCreateUserGroup(String groupName, String languageCodeWithRefset) throws ServiceExceptionWithStatusCode {
		WeblateGroup weblateGroup = getUserGroupByName(groupName);
		if (weblateGroup == null) {
			logger.info("User Group '{}' does not exist in Translation Tool, creating...", groupName);
			WeblateAddGroupRequest weblateGroupRequest = new WeblateAddGroupRequest();
			weblateGroupRequest.setName(groupName);
			weblateGroupRequest.setLanguageSelection(0);// 0 = As defined
			weblateGroupRequest.setProjectSelection(1);// 1 = All projects
			weblateGroup = createGroupWithTranslationRoles(weblateGroupRequest, languageCodeWithRefset);
		}
		return weblateGroup;
	}

	public WeblateGroup getUserGroupByName(String groupName) {
		Predicate<WeblateGroup> predicate = group -> groupName.equals(group.getName());
		Optional<WeblateGroup> groupOptional = userGroupCache.values().stream().filter(predicate).findFirst();
		if (groupOptional.isEmpty()) {
			List<WeblateGroup> groups = getGroups();
			for (WeblateGroup group : groups) {
				userGroupCache.put(group.getId(), group);
			}
			groupOptional = userGroupCache.values().stream().filter(predicate).findFirst();
		}
		return groupOptional.orElse(null);
	}

	private List<WeblateGroup> getGroups() {
		ResponseEntity<WeblateResponse<WeblateGroup>> response = restTemplate.exchange("/groups/?page_size=1000&format=json", HttpMethod.GET, null, GROUPS_RESPONSE_TYPE);
		WeblateResponse<WeblateGroup> weblateResponse = response.getBody();
		if (weblateResponse == null) {
			return new ArrayList<>();
		}
		return weblateResponse.getResults();
	}

	public WeblateGroup getUserGroup(Integer id) {
		try {
			ResponseEntity<WeblateGroup> response = restTemplate.exchange("/groups/%s/?format=json".formatted(id), HttpMethod.GET, null, WeblateGroup.class);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			WeblateGroup deletedGroup = new WeblateGroup();
			deletedGroup.setId(id);
			deletedGroup.setDeleted(true);
			return deletedGroup;
		}
	}

	public WeblateGroup createGroupWithTranslationRoles(WeblateAddGroupRequest weblateGroupRequest, String languageCodeRefsetId) throws ServiceExceptionWithStatusCode {
		ResponseEntity<WeblateGroup> response = restTemplate.exchange("/groups/", HttpMethod.POST, new HttpEntity<>(weblateGroupRequest, getJsonHeaders()), WeblateGroup.class);
		WeblateGroup weblateGroup = response.getBody();
		if (weblateGroup == null) {
			throw new ServiceExceptionWithStatusCode("Failed to create group %s".formatted(weblateGroupRequest.getName()), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		addGroupLanguage(weblateGroup, languageCodeRefsetId);
		addGroupRole(weblateGroup, ADD_SUGGESTION_ROLE);
		addGroupRole(weblateGroup, TRANSLATE_ROLE);
		addGroupRole(weblateGroup, AUTOMATIC_TRANSLATION_ROLE);
		addGroupRole(weblateGroup, REVIEW_STRINGS_ROLE);
		return weblateGroup;
	}

	private void addGroupLanguage(WeblateGroup weblateGroup, String languageCodeRefsetId) {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("language_code", languageCodeRefsetId);
		restTemplate.postForEntity("/groups/%s/languages/".formatted(weblateGroup.getId()), new HttpEntity<>(requestBody, getJsonHeaders()), String.class);
	}

	private void addGroupRole(WeblateGroup weblateGroup, String roleName) throws ServiceExceptionWithStatusCode {
		int roleId = getRoleId(roleName);
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("role_id", roleId + "");
		restTemplate.postForEntity("/groups/%s/roles/".formatted(weblateGroup.getId()), new HttpEntity<>(requestBody, getJsonHeaders()), String.class);
	}

	private int getRoleId(String roleName) throws ServiceExceptionWithStatusCode {
		WeblateRole role = roleCache.get(roleName);
		if (role == null) {
			loadRoles();
			role = roleCache.get(roleName);
		}
		if (role == null) {
			throw new ServiceExceptionWithStatusCode(String.format("Weblate role '%s' not found", roleName), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return role.getId();
	}

	private void loadRoles() throws ServiceExceptionWithStatusCode {
		ResponseEntity<WeblateResponse<WeblateRole>> response = restTemplate.exchange("/roles/?format=json", HttpMethod.GET, null, ROLES_RESPONSE_TYPE);
		WeblateResponse<WeblateRole> body = response.getBody();
		if (body == null) {
			throw new ServiceExceptionWithStatusCode("Failed to load Weblate roles.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		for (WeblateRole role : body.getResults()) {
			roleCache.put(role.getName(), role);
		}
	}

	public void addUserToGroup(WeblateUser weblateUser, WeblateGroup weblateGroup) {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("group_id", weblateGroup.getId() + "");
		restTemplate.postForEntity("/users/%s/groups/".formatted(weblateUser.getUsername()), new HttpEntity<>(requestBody, getJsonHeaders()), String.class);
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
			WeblateAddLanguageRequest addLanguageRequest = new WeblateAddLanguageRequest(languageCodeWithRefset, languageName,
				direction, 0, new WeblateAddLanguageRequestPlural(2, "n != 1"));

			restTemplate.exchange("/languages/", HttpMethod.POST, new HttpEntity<>(addLanguageRequest, getJsonHeaders()), String.class);
		} catch (HttpClientErrorException e) {
			throw new ServiceExceptionWithStatusCode(("Failed to create new language. " +
				"Translation Tool status code:%s").formatted(e.getStatusCode().value()), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	public boolean isTranslationExistsSearchByLanguageRefset(String languageCodeWithRefsetId) {
		try {
			restTemplate.getForEntity("/translations/common/snomedct/%s/?format=json".formatted(languageCodeWithRefsetId), String.class);
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

	public WeblateLabel getCreateLabel(String project, String label, String description) {
		try {
			WeblateLabel existing = getLabel(project, label);
			if (existing != null) return existing;

			WeblateLabel newLabel = new WeblateLabel(null, label, description, "blue");
			String url = getLabelsUrl(project);
			restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(newLabel, getJsonHeaders()), Void.class);
			return getLabel(project, label);
		} catch (HttpClientErrorException e) {
			return null;
		}
	}

	public WeblateLabel getLabel(String project, String label) {
		String url = getLabelsUrl(project);
		ResponseEntity<WeblatePage<WeblateLabel>> response = restTemplate.exchange(
			url,
			HttpMethod.GET,
			null,
			LABELS_RESPONSE_TYPE
		);
		WeblatePage<WeblateLabel> page = response.getBody();
		if (page != null) {
			Optional<WeblateLabel> existing = page.results().stream().filter(result -> result.name().equals(label)).findFirst();
			return existing.orElse(null);
		} else {
			return null;
		}
	}

	public void bulkAddLabels(String projectSlug, Integer labelId, List<String> contextIds) throws ServiceExceptionWithStatusCode {
		// This is a custom Weblate endpoint
		String url = "/units/bulk_add_label/";
		try {
			BulkAddLabelRequest request = new BulkAddLabelRequest(projectSlug, labelId, contextIds);
			restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, getJsonHeaders()), Void.class);
		} catch (HttpClientErrorException e) {
			throw new ServiceExceptionWithStatusCode("Error bulk assigning label: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private static @NotNull String getLabelsUrl(String project) {
		return "/projects/%s/labels/?format=json".formatted(project);
	}

	public void deleteLabelAsync(String project, String label) {
		// Run the deletion in background without error handling
		CompletableFuture.runAsync(() -> {
			try {
				WeblateLabel weblateLabel = getLabel(project, label);
				if (weblateLabel != null) {
					String url = "/projects/%s/labels/%s/".formatted(project, weblateLabel.id());
					restTemplate.delete(url);
				}
			} catch (Exception e) {
				// Fire and forget - no error handling
				logger.info("Background label deletion failed for project {} label {}: {}", project, label, e.getMessage());
			}
		});
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}
}
