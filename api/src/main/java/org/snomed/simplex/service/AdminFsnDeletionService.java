package org.snomed.simplex.service;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.FsnBulkRemovalStats;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.DeleteFsnDescriptionsResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Predicate;

import static org.snomed.simplex.client.domain.Description.Type.FSN;

@Service
public class AdminFsnDeletionService {

	private static final Logger logger = LoggerFactory.getLogger(AdminFsnDeletionService.class);

	public DeleteFsnDescriptionsResult deleteAllFsnDescriptions(CodeSystem codeSystem, SnowstormClient snowstormClient,
			boolean dryRun, String languageCode) throws ServiceException {

		String moduleId = codeSystem.getDefaultModule();
		if (Strings.isBlank(moduleId)) {
			throw new ServiceExceptionWithStatusCode(
					"CodeSystem %s has no default module.".formatted(codeSystem.getShortName()),
					HttpStatus.BAD_REQUEST);
		}

		String normalizedLanguage = normalizeLanguageCode(languageCode);
		LinkedHashSet<Long> conceptIds = snowstormClient.collectActiveFsnConceptIds(codeSystem, moduleId, normalizedLanguage);
		int conceptsWithFsn = conceptIds.size();
		if (normalizedLanguage == null) {
			logger.info("Found {} concepts with active FSN in module {} on branch {}.",
					conceptsWithFsn, moduleId, codeSystem.getWorkingBranchPath());
		} else {
			logger.info("Found {} concepts with active FSN in module {} language {} on branch {}.",
					conceptsWithFsn, moduleId, normalizedLanguage, codeSystem.getWorkingBranchPath());
		}

		if (dryRun || conceptsWithFsn == 0) {
			return new DeleteFsnDescriptionsResult(conceptsWithFsn, 0, 0, 0, 0);
		}

		Predicate<Description> shouldRemove = description -> description.isActive()
				&& description.getType() == FSN
				&& Objects.equals(moduleId, description.getModuleId())
				&& matchesLanguage(normalizedLanguage, description.getLang());

		FsnBulkRemovalStats stats = snowstormClient.bulkRemoveDescriptionsMatching(
				codeSystem, conceptIds, shouldRemove, "Removing FSN descriptions");

		logger.info("Deleted FSN descriptions for {}: {} concepts updated, {} inactivated, {} deleted, {} skipped.",
				codeSystem.getShortName(), stats.getConceptsUpdated(), stats.getFsnInactivated(),
				stats.getFsnDeleted(), stats.getFsnSkipped());

		return new DeleteFsnDescriptionsResult(
				conceptsWithFsn,
				stats.getConceptsUpdated(),
				stats.getFsnInactivated(),
				stats.getFsnDeleted(),
				stats.getFsnSkipped());
	}

	private static String normalizeLanguageCode(String languageCode) {
		if (languageCode == null || languageCode.isBlank()) {
			return null;
		}
		return languageCode.trim().toLowerCase();
	}

	private static boolean matchesLanguage(String normalizedLanguage, String descriptionLanguage) {
		if (normalizedLanguage == null) {
			return true;
		}
		return descriptionLanguage != null && normalizedLanguage.equals(descriptionLanguage.toLowerCase());
	}
}
