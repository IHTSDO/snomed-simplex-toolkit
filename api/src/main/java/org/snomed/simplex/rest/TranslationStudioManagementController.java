package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.RepairTranslationSetSizesResponse;
import org.snomed.simplex.rest.pojos.RepairTranslationUnitIdsResponse;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.snolate.service.SnolateSnomedUpgradeService;
import org.snomed.simplex.snolate.sets.SnolateSetService;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitMigrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Translation Studio Management")
@RequestMapping("api/translation-studio")
public class TranslationStudioManagementController {

	private final SnolateSnomedUpgradeService snolateSnomedUpgradeService;
	private final SnolateSetService snolateSetService;
	private final SnolateTranslationUnitMigrationService translationUnitMigrationService;

	public TranslationStudioManagementController(SnolateSnomedUpgradeService snolateSnomedUpgradeService,
			SnolateSetService snolateSetService,
			SnolateTranslationUnitMigrationService translationUnitMigrationService) {
		this.snolateSnomedUpgradeService = snolateSnomedUpgradeService;
		this.snolateSetService = snolateSetService;
		this.translationUnitMigrationService = translationUnitMigrationService;
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

	@PostMapping("repair-translation-unit-ids")
	@Operation(summary = "Merge duplicate translation units and re-key documents to canonical Elasticsearch ids.",
			description = "Run once per environment after deploy, before relying on canonical-id write paths. "
					+ "Optionally scope to one CodeSystem.")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public RepairTranslationUnitIdsResponse repairTranslationUnitIds(@RequestParam(required = false) String codeSystem) {
		return translationUnitMigrationService.repairTranslationUnitIds(codeSystem);
	}
}
