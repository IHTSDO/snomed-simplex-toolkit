package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.DeleteFsnDescriptionsResult;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.AdminFsnDeletionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@RestController
@RequestMapping("api/admin/{codeSystem}/data-cleanup")
@Tag(name = "Admin Data Cleanup", description = "Global admin data cleanup operations on a CodeSystem")
public class DataCleanupController {

	private final AdminFsnDeletionService adminFsnDeletionService;
	private final ActivityService activityService;
	private final SnowstormClientFactory snowstormClientFactory;

	public DataCleanupController(AdminFsnDeletionService adminFsnDeletionService,
			ActivityService activityService,
			SnowstormClientFactory snowstormClientFactory) {
		this.adminFsnDeletionService = adminFsnDeletionService;
		this.activityService = activityService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@GetMapping("/concepts-without-active-en-fsn")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "List default-module concepts without an active English FSN",
			description = "Returns concepts in the CodeSystem default module (active or inactive) that do not have any active English FSN. "
					+ "Matching uses Snowstorm description type and language only; US preferred-in refset is not applied due to a Snowstorm limitation.")
	public Page<ConceptMini> findConceptsWithoutActiveEnFsn(
			@PathVariable String codeSystem,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit) throws ServiceException {

		ControllerHelper.validatePageSize(offset, limit);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return adminFsnDeletionService.findConceptsWithoutActiveEnFsn(theCodeSystem, snowstormClient, offset, limit);
	}

	@PostMapping("/delete-fsn-descriptions")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Delete all active FSN descriptions in the CodeSystem default module",
			description = "Unreleased FSNs are hard-deleted; released FSNs are inactivated. "
					+ "Language refset members are cleaned up by Snowstorm. "
					+ "Concepts may be left without an active FSN in the extension module. "
					+ "Optional language limits removal to FSNs with that ISO language code (e.g. en, fr).")
	public DeleteFsnDescriptionsResult deleteFsnDescriptions(
			@PathVariable String codeSystem,
			@RequestParam(defaultValue = "false") boolean dryRun,
			@RequestParam(required = false) String language) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return activityService.runActivity(codeSystem, CODE_SYSTEM, ActivityType.UPDATE, () ->
				adminFsnDeletionService.deleteAllFsnDescriptions(theCodeSystem, snowstormClient, dryRun, language));
	}
}
