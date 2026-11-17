package org.snomed.simplex.translation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Listens for Managed Service (SI Authoring Platform) translation project/task branch events, on the
 * externally-named topic confirmed with the Managed Service team (not Simplex's own {@code jms.queue.prefix}).
 * <p>
 * This is wired up now even though the Managed Service side isn't implemented/deployed yet, on the assumption
 * it will publish to this destination once ready. There is no rejection/delete event on this (or any) topic
 * today - see {@code ManagedServiceTaskController} for the interim rejection path.
 * <p>
 * Payload shape mirrors Snowstorm's {@code branch.change} topic (confirmed from the {@code snowstorm} and
 * {@code authoring-services} source): a flat {@code {branch, sourceBranch?}} object per commit. See
 * docs/managed-service-integration.md §4.3.
 */
@Component
public class ManagedServiceBranchEventListener {

	private static final String BRANCH = "branch";
	private static final String SOURCE_BRANCH = "sourceBranch";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ManagedServiceTaskEventService taskEventService;
	private final SnowstormClientFactory snowstormClientFactory;

	public ManagedServiceBranchEventListener(ManagedServiceTaskEventService taskEventService,
			SnowstormClientFactory snowstormClientFactory) {
		this.taskEventService = taskEventService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@JmsListener(destination = "${managed-service.jms.branch-events-destination}")
	public void receiveBranchEvent(Map<String, String> jsonObject) {
		String branch = jsonObject.get(BRANCH);
		String sourceBranch = jsonObject.get(SOURCE_BRANCH);
		if (branch == null || sourceBranch == null) {
			// No sourceBranch: a plain commit (e.g. save), not a promotion or rebase - not relevant here.
			return;
		}

		boolean isPromotion = sourceBranch.contains(branch);
		if (!isPromotion) {
			// Rebase of the translation project branch from the code system branch.
			// REBASE handling (content pull-in, international content upgrade, translation set refresh)
			// is not yet implemented - see docs/managed-service-integration.md §4.3.
			logger.info("Managed Service REBASE event received for project branch {} from {} - handling not yet implemented", branch, sourceBranch);
			return;
		}

		// Promotion: branch is the project branch being promoted into, sourceBranch is the task branch.
		try {
			Optional<CodeSystem> codeSystem = findCodeSystemForProjectBranch(branch);
			if (codeSystem.isEmpty()) {
				logger.warn("Managed Service PROMOTE event for project branch {} did not match a known code system", branch);
				return;
			}
			int updated = taskEventService.handleTaskPromoted(codeSystem.get().getShortName());
			logger.info("Managed Service PROMOTE event: project branch {} (task {}) promoted, transitioned {} unit(s)",
					branch, sourceBranch, updated);
		} catch (ServiceException e) {
			logger.error("Error while processing Managed Service PROMOTE event for project branch {}: {}", branch, e.getMessage(), e);
		}
	}

	private Optional<CodeSystem> findCodeSystemForProjectBranch(String projectBranchPath) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		List<CodeSystem> codeSystems = snowstormClient.getCodeSystems(false);
		int lastSlash = projectBranchPath.lastIndexOf('/');
		if (lastSlash < 0) {
			return Optional.empty();
		}
		String parentBranchPath = projectBranchPath.substring(0, lastSlash);
		return codeSystems.stream().filter(cs -> parentBranchPath.equals(cs.getBranchPath())).findFirst();
	}
}
