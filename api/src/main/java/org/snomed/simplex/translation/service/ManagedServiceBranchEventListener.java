package org.snomed.simplex.translation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Listens for Managed Service (SI Authoring Platform) translation branch events, on the externally-named
 * destination confirmed with the Managed Service team (not Simplex's own {@code jms.queue.prefix}).
 * <p>
 * Message format: see {@link ManagedServiceBranchEvent}. Events handled:
 * <ul>
 *   <li>{@code REBASE} of the translation project branch: content may have come in from the code system, so
 *       translation content is pulled from Snowstorm.</li>
 *   <li>{@code PROMOTE} of a task into the project: units {@code PROMOTED_TO_TASK} become {@code COMPLETE}, then content
 *       (including any edits made in the task) is pulled from Snowstorm.</li>
 *   <li>{@code DELETE} of a task branch: units {@code PROMOTED_TO_TASK} revert to {@code APPROVED}.</li>
 * </ul>
 * There is no logged-in user on a listener thread, so Snowstorm is called as the configured service account.
 */
@Component
public class ManagedServiceBranchEventListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ManagedServiceTaskEventService taskEventService;
	private final SnowstormClientFactory snowstormClientFactory;
	private final AuthenticationClient authenticationClient;
	private final ObjectMapper objectMapper;
	private final String serviceAccountUsername;
	private final String serviceAccountPassword;

	public ManagedServiceBranchEventListener(ManagedServiceTaskEventService taskEventService,
			SnowstormClientFactory snowstormClientFactory,
			AuthenticationClient authenticationClient,
			ObjectMapper objectMapper,
			@Value("${managed-service.service-account.username:}") String serviceAccountUsername,
			@Value("${managed-service.service-account.password:}") String serviceAccountPassword) {

		this.taskEventService = taskEventService;
		this.snowstormClientFactory = snowstormClientFactory;
		this.authenticationClient = authenticationClient;
		this.objectMapper = objectMapper;
		this.serviceAccountUsername = serviceAccountUsername;
		this.serviceAccountPassword = serviceAccountPassword;
	}

	@JmsListener(destination = "${managed-service.jms.branch-events-destination}")
	public void receiveBranchEvents(String messageBody) {
		List<ManagedServiceBranchEvent> events;
		try {
			JsonNode message = objectMapper.readTree(messageBody);
			events = ManagedServiceBranchEvent.parse(message);
		} catch (Exception e) {
			logger.error("Could not parse Managed Service branch event message: {}", e.getMessage(), e);
			return;
		}
		if (events.isEmpty()) {
			return;
		}
		if (!StringUtils.hasLength(serviceAccountUsername)) {
			logger.warn("Ignoring {} Managed Service branch event(s): managed-service.service-account.username is not configured", events.size());
			return;
		}

		try {
			authenticateAsServiceAccount();
			for (ManagedServiceBranchEvent event : events) {
				handleEvent(event);
			}
		} catch (Exception e) {
			logger.error("Error while processing Managed Service branch event(s): {}", e.getMessage(), e);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	void handleEvent(ManagedServiceBranchEvent event) {
		try {
			// A task branch is a child of the project branch.
			String projectBranch = event.type() == ManagedServiceBranchEvent.Type.DELETE ? parentBranch(event.branchPath()) : event.branchPath();
			Optional<CodeSystem> codeSystem = projectBranch == null ? Optional.empty() : findCodeSystemForProjectBranch(projectBranch);
			if (codeSystem.isEmpty()) {
				logger.warn("Managed Service {} event for branch {} did not match a known code system", event.type(), event.branchPath());
				return;
			}
			String shortName = codeSystem.get().getShortName();
			switch (event.type()) {
				case REBASE -> {
					logger.info("Managed Service REBASE event: project branch {} rebased from {}", event.branchPath(), event.sourceBranchPath());
					taskEventService.pullTranslationContentFromSnowstorm(codeSystem.get(), snowstormClientFactory.getClient());
				}
				case PROMOTE -> {
					int updated = taskEventService.handleTaskPromoted(shortName);
					logger.info("Managed Service PROMOTE event: project branch {} (task {}) promoted, transitioned {} unit(s)",
							event.branchPath(), event.sourceBranchPath(), updated);
					// Edits made directly in the task are now in the project: pull them and refresh the project snapshot.
					taskEventService.pullTranslationContentFromSnowstorm(codeSystem.get(), snowstormClientFactory.getClient());
				}
				case DELETE -> {
					int updated = taskEventService.handleTaskRejected(shortName);
					logger.info("Managed Service DELETE event: task branch {} deleted, reverted {} unit(s)", event.branchPath(), updated);
				}
			}
		} catch (ServiceException e) {
			logger.error("Error while processing Managed Service {} event for branch {}: {}", event.type(), event.branchPath(), e.getMessage(), e);
		}
	}

	private void authenticateAsServiceAccount() throws ServiceException {
		String token = authenticationClient.fetchAuthenticationToken(serviceAccountUsername, serviceAccountPassword);
		SecurityContext context = new SecurityContextImpl(
				new PreAuthenticatedAuthenticationToken(serviceAccountUsername, token, List.of()));
		SecurityContextHolder.setContext(context);
	}

	private Optional<CodeSystem> findCodeSystemForProjectBranch(String projectBranchPath) throws ServiceException {
		String parentBranchPath = parentBranch(projectBranchPath);
		if (parentBranchPath == null) {
			return Optional.empty();
		}
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return snowstormClient.getCodeSystems(false).stream()
				.filter(cs -> parentBranchPath.equals(cs.getBranchPath()))
				.findFirst();
	}

	static String parentBranch(String branchPath) {
		int lastSlash = branchPath.lastIndexOf('/');
		return lastSlash < 0 ? null : branchPath.substring(0, lastSlash);
	}
}
