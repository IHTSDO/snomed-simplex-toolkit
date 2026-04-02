package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.snolate.service.SnolateSnomedUpgradeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Snolate Management")
@RequestMapping("api/snolate")
public class SnolateManagementController {

	private final SnolateSnomedUpgradeService snolateSnomedUpgradeService;

	public SnolateManagementController(SnolateSnomedUpgradeService snolateSnomedUpgradeService) {
		this.snolateSnomedUpgradeService = snolateSnomedUpgradeService;
	}

	@PostMapping("snomed-initialise")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public TranslationToolUpdatePlan snomedInitialise() throws ServiceExceptionWithStatusCode {
		return snolateSnomedUpgradeService.runUpdate(true, "Initialise SNOMED CT in Snolate", null);
	}

	@PostMapping("snomed-upgrade")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public TranslationToolUpdatePlan snomedUpgrade(@RequestParam(required = false) Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		return snolateSnomedUpgradeService.runUpdate(false, "Upgrade SNOMED CT in Snolate", upgradeToEffectiveTime);
	}
}
