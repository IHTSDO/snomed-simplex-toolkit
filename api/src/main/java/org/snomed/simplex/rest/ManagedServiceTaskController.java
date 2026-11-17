package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.translation.service.ManagedServiceTaskEventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interim path for Managed Service (SI Authoring Platform) translation task lifecycle events that have no
 * JMS signal yet - currently just task rejection/deletion. There is no branch-deletion event anywhere in
 * Snowstorm or authoring-services today, so this controller is the way to trigger (and test) the
 * {@code PROMOTED_TO_TASK} -&gt; {@code APPROVED} revert until the Managed Service team adds a real event.
 * See docs/managed-service-integration.md §4.3/§6.2.
 */
@RestController
@RequestMapping("api/admin/{codeSystem}/managed-service")
@Tag(name = "Admin Managed Service", description = "Interim admin triggers for Managed Service task lifecycle events")
public class ManagedServiceTaskController {

	private final ManagedServiceTaskEventService taskEventService;

	public ManagedServiceTaskController(ManagedServiceTaskEventService taskEventService) {
		this.taskEventService = taskEventService;
	}

	@PostMapping("/task-rejected")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Report that the Managed Service translation task was rejected/deleted",
			description = "Reverts every translation unit currently PROMOTED_TO_TASK for this code system back to APPROVED.")
	public int taskRejected(@PathVariable String codeSystem) {
		return taskEventService.handleTaskRejected(codeSystem);
	}

	@PostMapping("/task-promoted")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Report that the Managed Service translation task was promoted",
			description = "Transitions every translation unit currently PROMOTED_TO_TASK for this code system to COMPLETE. " +
					"Normally driven by the JMS PROMOTE event (ManagedServiceBranchEventListener); exposed here for testing.")
	public int taskPromoted(@PathVariable String codeSystem) {
		return taskEventService.handleTaskPromoted(codeSystem);
	}
}
