package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.DeleteEmptyShellUnitsResponse;
import org.snomed.simplex.rest.pojos.RepairTranslationSetSizesResponse;
import org.snomed.simplex.rest.pojos.RepairTranslationUnitIdsResponse;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.snolate.service.SnolateSnomedUpgradeService;
import org.snomed.simplex.snolate.sets.SnolateSetService;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitMigrationService;
import org.snomed.simplex.translation.service.TranslationCopyPathMigrationService;
import org.snomed.simplex.translation.service.TranslationCopyPathMigrationService.MigrationResult;
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
	private final TranslationCopyPathMigrationService translationCopyPathMigrationService;

	public TranslationStudioManagementController(SnolateSnomedUpgradeService snolateSnomedUpgradeService,
			SnolateSetService snolateSetService,
			SnolateTranslationUnitMigrationService translationUnitMigrationService,
			TranslationCopyPathMigrationService translationCopyPathMigrationService) {
		this.snolateSnomedUpgradeService = snolateSnomedUpgradeService;
		this.snolateSetService = snolateSetService;
		this.translationUnitMigrationService = translationUnitMigrationService;
		this.translationCopyPathMigrationService = translationCopyPathMigrationService;
	}

	@PostMapping("snomed-initialise")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Initialise SNOMED CT in Snolate.",
			description = "Optionally initialise using an older version by giving its effective time (yyyymmdd). "
					+ "Defaults to the latest version. Use snomed-upgrade later to move to a newer version.")
	public TranslationToolUpdatePlan snomedInitialise(@RequestParam(required = false) Integer effectiveTime) throws ServiceExceptionWithStatusCode {
		return snolateSnomedUpgradeService.runUpdate(true, "Initialise SNOMED CT in Snolate", effectiveTime);
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

	@PostMapping("delete-empty-shell-units")
	@Operation(summary = "Delete empty shell translation units for a CodeSystem.",
			description = "Removes TranslationUnit documents with no terms and NOT_STARTED status across all "
					+ "language refsets on the edition, then recalculates translation set sizes.")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public DeleteEmptyShellUnitsResponse deleteEmptyShellUnits(@RequestParam String codeSystem)
			throws ServiceExceptionWithStatusCode {
		ServiceHelper.requiredParameter("codeSystem", codeSystem);
		return translationUnitMigrationService.deleteEmptyShellUnits(codeSystem);
	}

	@PostMapping("migrate-translation-copy")
	@Operation(summary = "Migrate translation-copy snapshots from the old flat storage path to the new code-system-prefixed path.",
			description = "Runs the same migration performed automatically on startup (see TranslationCopyPathMigrationService), "
					+ "but on demand and using the caller's own authentication token rather than the managed-service account. "
					+ "Safe to run repeatedly; already-migrated or never-existing files are left alone.")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public MigrationResult migrateTranslationCopy() throws ServiceException {
		return translationCopyPathMigrationService.migrateOnDemand();
	}
}
