package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.RepairTranslationSetSizesResponse;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.snolate.service.SnolateSnomedUpgradeService;
import org.snomed.simplex.snolate.sets.SnolateSetService;
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
	private final SnolateSetService snolateSetService;

	public SnolateManagementController(SnolateSnomedUpgradeService snolateSnomedUpgradeService,
			SnolateSetService snolateSetService) {
		this.snolateSnomedUpgradeService = snolateSnomedUpgradeService;
		this.snolateSetService = snolateSetService;
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

	@PostMapping("repair-set-sizes")
	@Operation(summary = "Recalculate stored translation set sizes from Elasticsearch unit counts.",
			description = "Use after fixing size calculation or to repair historical sets. Optionally scope to one CodeSystem.")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public RepairTranslationSetSizesResponse repairSetSizes(@RequestParam(required = false) String codeSystem) {
		return snolateSetService.repairSetSizes(codeSystem);
	}
}
