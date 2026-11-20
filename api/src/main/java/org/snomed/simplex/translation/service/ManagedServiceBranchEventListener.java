package org.snomed.simplex.translation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.EditionEventService;
import org.snomed.simplex.service.job.TranslationStudioContentJob;
import org.snomed.simplex.snolate.service.TranslationStudioImportJobRecordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private static final String JOB_USERNAME = "Authoring Platform";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ManagedServiceTaskEventService taskEventService;
	private final SnowstormClientFactory snowstormClientFactory;
	private final AuthenticationClient authenticationClient;
	private final ObjectMapper objectMapper;
	private final TranslationStudioImportJobRecordService jobRecordService;
	private final EditionEventService editionEventService;
	private final String serviceAccountUsername;
	private final String serviceAccountPassword;
	private final String devWorkaroundImsToken;

	public ManagedServiceBranchEventListener(ManagedServiceTaskEventService taskEventService,
			SnowstormClientFactory snowstormClientFactory,
			AuthenticationClient authenticationClient,
			ObjectMapper objectMapper,
			TranslationStudioImportJobRecordService jobRecordService,
			EditionEventService editionEventService,
			@Value("${managed-service.service-account.username:}") String serviceAccountUsername,
			@Value("${managed-service.service-account.password:}") String serviceAccountPassword,
			@Value("${managed-service.service-account.dev-workaround-ims-token:}") String devWorkaroundImsToken) {

		this.taskEventService = taskEventService;
		this.snowstormClientFactory = snowstormClientFactory;
		this.authenticationClient = authenticationClient;
		this.objectMapper = objectMapper;
		this.jobRecordService = jobRecordService;
		this.editionEventService = editionEventService;
		this.serviceAccountUsername = serviceAccountUsername;
		this.serviceAccountPassword = serviceAccountPassword;
		this.devWorkaroundImsToken = devWorkaroundImsToken;
	}

	@JmsListener(destination = "${managed-service.jms.branch-events-destination}")
	public void receiveBranchEvents(String messageBody) {
		List<ManagedServiceBranchEvent> events;
		try {
			JsonNode message = objectMapper.readTree(messageBody);
			events = ManagedServiceBranchEvent.parse(message);
		} catch (Exception e) {
			logger.error("Could not parse Authoring Platform branch event message: {}", e.getMessage(), e);
			return;
		}
		if (events.isEmpty()) {
			return;
		}
		if (!hasCredentials() && !StringUtils.hasLength(devWorkaroundImsToken)) {
			logger.warn("Ignoring {} Authoring Platform branch event(s): neither managed-service.service-account.username/password nor "
					+ "managed-service.service-account.dev-workaround-ims-token is configured", events.size());
			return;
		}

		try {
			authenticateAsServiceAccount();
			for (ManagedServiceBranchEvent event : events) {
				handleEvent(event);
			}
		} catch (Exception e) {
			logger.error("Error while processing Authoring Platform branch event(s): {}", e.getMessage(), e);
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
				logger.warn("Authoring Platform {} event for branch {} did not match a known code system", event.type(), event.branchPath());
				return;
			}
			String shortName = codeSystem.get().getShortName();
			switch (event.type()) {
				case REBASE -> {
					logger.info("Authoring Platform REBASE event: project branch {} rebased from {}", event.branchPath(), event.sourceBranchPath());
					pullAndRecord(codeSystem.get(), TranslationSyncTrigger.PROJECT_REBASED, null);
				}
				case PROMOTE -> {
					int updated = taskEventService.handleTaskPromoted(shortName);
					logger.info("Authoring Platform PROMOTE event: project branch {} (task {}) promoted, transitioned {} unit(s)",
							event.branchPath(), event.sourceBranchPath(), updated);
					// Edits made directly in the task are now in the project: pull them and refresh the project snapshot.
					pullAndRecord(codeSystem.get(), TranslationSyncTrigger.TASK_PROMOTED, updated);
				}
				case DELETE -> {
					int updated = taskEventService.handleTaskRejected(shortName);
					logger.info("Authoring Platform DELETE event: task branch {} deleted, reverted {} unit(s)", event.branchPath(), updated);
					editionEventService.publish(shortName, EditionEventService.EVENT_TRANSLATION_TASK_REVERTED, Map.of(
							"taskBranch", event.branchPath(),
							"unitsReverted", updated));
				}
			}
		} catch (ServiceException e) {
			logger.error("Error while processing Authoring Platform {} event for branch {}: {}", event.type(), event.branchPath(), e.getMessage(), e);
		}
	}

	/**
	 * Pull translation content from Snowstorm, leave a record in the Translation Studio jobs log
	 * and tell any browsers viewing the edition.
	 */
	private void pullAndRecord(CodeSystem codeSystem, TranslationSyncTrigger trigger, Integer unitsCompleted) {
		String shortName = codeSystem.getShortName();
		TranslationStudioContentJob job = new TranslationStudioContentJob(codeSystem,
				"Translation Studio sync from Snowstorm (%s)".formatted(trigger.getLabel()), null);
		job.setUsername(JOB_USERNAME);
		job.setStatus(JobStatus.IN_PROGRESS);
		int refsetsSynchronised = 0;
		try {
			ManagedServiceTaskEventService.PullResult result = taskEventService.pullTranslationContentFromSnowstorm(codeSystem, snowstormClientFactory.getClient());
			refsetsSynchronised = result.synchronised();
			if (result.hasFailures()) {
				job.setStatus(JobStatus.SYSTEM_ERROR);
				job.setErrorMessage("Failed to sync %d language refset(s): %s".formatted(result.failures().size(), String.join("; ", result.failures())));
			} else {
				job.setStatus(JobStatus.COMPLETE);
			}
		} catch (Exception e) {
			logger.error("Snowstorm sync after {} failed for {}: {}", trigger.getLabel(), shortName, e.getMessage(), e);
			job.setStatus(JobStatus.SYSTEM_ERROR);
			job.setErrorMessage(e.getMessage());
		}
		try {
			jobRecordService.saveFromJob(job);
		} catch (Exception e) {
			logger.error("Could not save sync job record for {}: {}", shortName, e.getMessage(), e);
		}
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("trigger", trigger.name());
		eventData.put("status", job.getStatus().name());
		eventData.put("jobId", job.getId());
		eventData.put("errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : "");
		eventData.put("refsetsSynchronised", refsetsSynchronised);
		if (unitsCompleted != null) {
			eventData.put("unitsCompleted", unitsCompleted);
		}
		editionEventService.publish(shortName, EditionEventService.EVENT_TRANSLATION_SYNC, eventData);
	}

	private boolean hasCredentials() {
		return StringUtils.hasLength(serviceAccountUsername) && StringUtils.hasLength(serviceAccountPassword);
	}

	private void authenticateAsServiceAccount() throws ServiceException {
		String principal = serviceAccountUsername;
		String token;
		if (hasCredentials()) {
			token = authenticationClient.fetchAuthenticationToken(serviceAccountUsername, serviceAccountPassword);
		} else {
			// Temporary workaround: no username/password configured, use the fixed IMS token.
			logger.warn("Using managed-service.service-account.dev-workaround-ims-token to authenticate; this is a temporary workaround");
			token = devWorkaroundImsToken;
			principal = StringUtils.hasLength(principal) ? principal : JOB_USERNAME;
		}
		SecurityContext context = new SecurityContextImpl(
				new PreAuthenticatedAuthenticationToken(principal, token, List.of()));
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
