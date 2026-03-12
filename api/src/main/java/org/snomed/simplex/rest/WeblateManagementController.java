package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.weblate.WeblateSnomedUpgradeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Weblate Management")
@RequestMapping("api/weblate")
public class WeblateManagementController {

	private final WeblateSnomedUpgradeService weblateSnomedUpgradeService;

	public WeblateManagementController(WeblateSnomedUpgradeService weblateSnomedUpgradeService) {
		this.weblateSnomedUpgradeService = weblateSnomedUpgradeService;
	}

	@PostMapping("snomed-initialise")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public TranslationToolUpdatePlan translationToolSnomedInitialise() throws ServiceExceptionWithStatusCode {
		return weblateSnomedUpgradeService.runUpdate(true, "Initialise SNOMED CT in Translation Tool", null);
	}

	@PostMapping("snomed-upgrade")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public TranslationToolUpdatePlan translationToolSnomedUpgrade(@RequestParam(required = false) Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		return weblateSnomedUpgradeService.runUpdate(false, "Upgrade SNOMED CT in Translation Tool", upgradeToEffectiveTime);
	}

}
