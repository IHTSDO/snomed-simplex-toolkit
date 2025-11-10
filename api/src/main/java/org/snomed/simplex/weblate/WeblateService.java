package org.snomed.simplex.weblate;

import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.external.WeblateLanguageInitialisationJobService;
import org.snomed.simplex.weblate.domain.WeblateComponent;
import org.snomed.simplex.weblate.domain.WeblateGroup;
import org.snomed.simplex.weblate.domain.WeblateUser;
import org.snomed.simplex.weblate.domain.WeblateUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class WeblateService {

	public static final String EN = "en";

	@Value("${weblate.common.project}")
	public String commonProject;
	private final SnowstormClientFactory snowstormClientFactory;
	private final WeblateClientFactory weblateClientFactory;
	private final ExecutorService addLanguageExecutorService;
	private final SupportRegister supportRegister;
	private final AuthenticationClient authenticationClient;
	private final WeblateLanguageInitialisationJobService weblateLanguageInitialisationJobService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateService(SnowstormClientFactory snowstormClientFactory, WeblateClientFactory weblateClientFactory, SupportRegister supportRegister,
		AuthenticationClient authenticationClient, WeblateLanguageInitialisationJobService weblateLanguageInitialisationJobService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.weblateClientFactory = weblateClientFactory;
		this.supportRegister = supportRegister;
		this.authenticationClient = authenticationClient;
		this.weblateLanguageInitialisationJobService = weblateLanguageInitialisationJobService;
		addLanguageExecutorService = Executors.newFixedThreadPool(1, new DefaultThreadFactory("Weblate-add-language-thread"));
	}

	public WeblateUser getCreateWeblateUser() {
		WeblateAdminClient adminClient = weblateClientFactory.getAdminClient();
		String username = SecurityUtil.getUsername();
		WeblateUser weblateUser = adminClient.getWeblateUser(username);

		if (weblateUser == null) {
			logger.info("Automatically creating new Weblate user {}", username);
			weblateUser = adminClient.createUser(getUserDetails());
		}
		if (weblateUser.getUsername().equals(weblateUser.getFullName())) {
			// Fix name
			logger.info("Automatically updating Weblate user details {}", username);
			adminClient.updateDetails(getUserDetails());
		}
		return weblateUser;
	}

	public void runUserAccessCheck(Set<String> requiredLanguageCodeRefsetIds) throws ServiceExceptionWithStatusCode {
		WeblateUser weblateUser = getCreateWeblateUser();
		WeblateAdminClient adminClient = weblateClientFactory.getAdminClient();
		Set<WeblateGroup> usersCurrentGroups = adminClient.getUserGroups(weblateUser);

		// Example "Translation Team nl-58888888102"
		for (String requiredLanguageCodeRefsetId : requiredLanguageCodeRefsetIds) {
			String requiredGroupName = getGroupName(requiredLanguageCodeRefsetId);
			Optional<WeblateGroup> groupOptional = usersCurrentGroups.stream().filter(group -> requiredGroupName.equals(group.getName())).findFirst();
			if (groupOptional.isEmpty()) {
				// User is not in required group
				WeblateGroup weblateGroup = adminClient.getCreateUserGroup(requiredGroupName, requiredLanguageCodeRefsetId);
				logger.info("Automatically adding Weblate user {} to group '{}'", weblateUser.getUsername(), requiredGroupName);
				adminClient.addUserToGroup(weblateUser, weblateGroup);
			}
		}
	}

	private String getGroupName(String languageCodeRefsetId) {
		return "Translation Team %s".formatted(languageCodeRefsetId);
	}

	private AuthenticationClient.UserDetails getUserDetails() {
		return authenticationClient.fetchUserDetails(SecurityUtil.getAuthenticationToken());
	}

	public Page<WeblateComponent> getSharedSets() throws ServiceException {
		List<WeblateComponent> components = weblateClientFactory.getClient().listComponents(commonProject);
		components = components.stream().filter(c -> !c.slug().equals("glossary")).toList();
		return new Page<>(components);
	}

	@Async
	public void createConceptSet(String branch, String focusConcept, File outputFile, SecurityContext securityContext) throws ServiceException, IOException {
		try (OutputStream outputStream = new FileOutputStream(outputFile)) {
			SecurityContextHolder.setContext(securityContext);
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			snowstormClient.getBranchOrThrow(branch);
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
				// source,target,context,developer_comments
				// "Adenosine deaminase 2 deficiency","Adenosine deaminase 2 deficiency",987840791000119102,"http://snomed.info/id/987840791000119102 - Adenosine deaminase 2 deficiency (disorder)"
				writer.write("source,target,context,developer_comments");
				writer.newLine();

				Supplier<ConceptMini> conceptStream = snowstormClient.getConceptSortedHierarchyStream(branch, focusConcept);
				ConceptMini concept;
				while ((concept = conceptStream.get()) != null) {
					writer.write("\"");
					writer.write(concept.getPtOrFsnOrConceptId());
					writer.write("\",\"");
					writer.write(concept.getPtOrFsnOrConceptId());
					writer.write("\",");
					writer.write(concept.getConceptId());
					writer.write(",\"");
					writer.write(String.format("http://snomed.info/id/%s - %s", concept.getConceptId(), concept.getFsnTermOrConceptId()));
					writer.write("\"");
					writer.newLine();
				}
				logger.info("Created concept set {}/{}", branch, focusConcept);
			}
		}
	}

	public List<WeblateUser> getUsersForLanguage(String languageCode, String refsetId) {
		String languageCodeWithRefset = "%s-%s".formatted(languageCode, refsetId);
		String groupName = getGroupName(languageCodeWithRefset);
		WeblateAdminClient adminClient = weblateClientFactory.getAdminClient();

		// Step 1: Fetch all groups and find the one matching the refset
		WeblateGroup targetGroup = adminClient.getUserGroupByName(groupName);

		if (targetGroup == null) {
			logger.info("No translation team group found for refset: {}", refsetId);
			return new ArrayList<>();
		}

		// Step 2: Fetch all users and filter by the target group
		List<WeblateUserResponse> allUsers = adminClient.listUsers();
		String targetGroupUrl = targetGroup.getUrl();

		List<WeblateUser> usersInGroup = allUsers.stream()
			.filter(user -> user.getGroups() != null && user.getGroups().contains(targetGroupUrl))
			.map(user -> {
				WeblateUser simpleUser = new WeblateUser();
				simpleUser.setId(user.getId());
				simpleUser.setUsername(user.getUsername());
				simpleUser.setFullName(user.getFullName());
				simpleUser.setEmail(user.getEmail());
				return simpleUser;
			})
			.toList();

		logger.info("Found {} users in translation team for refset: {}", usersInGroup.size(), refsetId);
		return usersInGroup;
	}

	public void initialiseLanguageAndTranslationAsync(ConceptMini langRefset, String languageCodeWithRefset, Consumer<ServiceException> errorCallback) {

		SecurityContext securityContext = SecurityContextHolder.getContext();

		addLanguageExecutorService.submit(() ->{
			SecurityContextHolder.setContext(securityContext);
			try {
				WeblateAdminClient weblateAdminClient = weblateClientFactory.getAdminClient();

				// LanguageCode format = lang-refsetid, example fr-100000100
				if (!weblateAdminClient.isLanguageExists(languageCodeWithRefset)) {
					logger.info("Language {} does not exist in Translation Tool, creating...", languageCodeWithRefset);
					String refsetTerm = langRefset.getPtOrFsnOrConceptId();
					String leftToRight = "ltr";
					// This request is quick because it's not creating any terms.
					weblateAdminClient.createLanguage(languageCodeWithRefset, refsetTerm, leftToRight);
				}

				String groupName = getGroupName(languageCodeWithRefset);
				weblateAdminClient.getCreateUserGroup(groupName, languageCodeWithRefset);

				if (!weblateAdminClient.isTranslationExistsSearchByLanguageRefset(languageCodeWithRefset)) {
					logger.info("Translation {} does not exist in Translation Tool, creating...", languageCodeWithRefset);
					// This request takes a long time because it's creating a new translation of the terms.
					weblateAdminClient.createTranslation(languageCodeWithRefset);
				}
			} catch (ServiceExceptionWithStatusCode e) {
				supportRegister.handleSystemError(CodeSystem.SHARED, "Failed to add Translation Tool language.", e);
				errorCallback.accept(e);
			}
		});
	}

	public WeblateLanguageInitialisationJobService getWeblateLanguageInitialisationJobService() {
		return weblateLanguageInitialisationJobService;
	}
}
