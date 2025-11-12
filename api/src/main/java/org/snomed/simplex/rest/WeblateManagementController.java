package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.weblate.WeblateSnomedUpgradeService;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Weblate Management")
@RequestMapping("api/weblate")
public class WeblateManagementController {

	private final WeblateSnomedUpgradeService weblateSnomedUpgradeService;
	private final ContentProcessingJobService jobService;
	private final SnowstormClientFactory snowstormClientFactory;

	public WeblateManagementController(WeblateSnomedUpgradeService weblateSnomedUpgradeService, ContentProcessingJobService jobService,
			SnowstormClientFactory snowstormClientFactory) {
		this.weblateSnomedUpgradeService = weblateSnomedUpgradeService;
		this.jobService = jobService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@PostMapping("snomed-initialise")
	@PostAuthorize("hasPermission('AUTHOR', '')")
	public TranslationToolUpdatePlan translationToolSnomedInitialise() throws ServiceExceptionWithStatusCode {
		return doUpdate(true, "Initialise SNOMED CT in Translation Tool", null);
	}

	@PostMapping("snomed-upgrade")
	@PostAuthorize("hasPermission('AUTHOR', '')")
	public TranslationToolUpdatePlan translationToolSnomedUpgrade(@RequestParam(required = false) Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		return doUpdate(false, "Upgrade SNOMED CT in Translation Tool", upgradeToEffectiveTime);
	}

	private TranslationToolUpdatePlan doUpdate(boolean initial, String message, Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		CodeSystem rootCodeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);
		TranslationToolUpdatePlan updatePlan = weblateSnomedUpgradeService.getUpdatePlan(initial, upgradeToEffectiveTime);

		Activity activity = new Activity("SNOMEDCT", ComponentType.TRANSLATION,
			initial ? ActivityType.WEBLATE_SNOMED_INITIALISATION : ActivityType.WEBLATE_SNOMED_UPGRADE);
		ContentJob contentJob = new ContentJob(rootCodeSystem, message, null);
		jobService.queueContentJob(contentJob, null, activity, job -> weblateSnomedUpgradeService.runSnomedUpgrade(updatePlan, job));

		return updatePlan;
	}

}
