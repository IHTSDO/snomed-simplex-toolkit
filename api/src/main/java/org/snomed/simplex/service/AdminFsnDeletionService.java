package org.snomed.simplex.service;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.FsnBulkRemovalStats;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.DeleteFsnDescriptionsResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.Predicate;

import static org.snomed.simplex.client.domain.Description.Type.FSN;

@Service
public class AdminFsnDeletionService {

	private static final Logger logger = LoggerFactory.getLogger(AdminFsnDeletionService.class);

	public Page<ConceptMini> findConceptsWithoutActiveEnFsn(CodeSystem codeSystem, SnowstormClient snowstormClient,
			int offset, int limit) throws ServiceException {

		String moduleId = requireDefaultModuleId(codeSystem);
		Set<String> inModule = snowstormClient.collectConceptIdsInModule(codeSystem, moduleId);
		Set<String> withUsPreferredEnFsn = snowstormClient.collectConceptIdsWithUsPreferredActiveEnFsn(codeSystem, moduleId);
		List<String> missing = inModule.stream()
				.filter(id -> !withUsPreferredEnFsn.contains(id))
				.sorted(Comparator.comparingLong(Long::parseLong))
				.toList();

		int fromIndex = Math.min(offset, missing.size());
		int toIndex = Math.min(offset + limit, missing.size());
		List<ConceptMini> pageItems = new ArrayList<>();
		for (String conceptId : missing.subList(fromIndex, toIndex)) {
			pageItems.add(snowstormClient.getConceptMini(conceptId, codeSystem));
		}
		return new Page<>(pageItems, (long) missing.size());
	}

	public DeleteFsnDescriptionsResult deleteAllFsnDescriptions(CodeSystem codeSystem, SnowstormClient snowstormClient,
			boolean dryRun, String languageCode) throws ServiceException {

		String moduleId = requireDefaultModuleId(codeSystem);

		String normalizedLanguage = normalizeLanguageCode(languageCode);
		Set<Long> conceptIds = snowstormClient.collectActiveFsnConceptIds(codeSystem, moduleId, normalizedLanguage);
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

	private static String requireDefaultModuleId(CodeSystem codeSystem) throws ServiceExceptionWithStatusCode {
		String moduleId = codeSystem.getDefaultModule();
		if (Strings.isBlank(moduleId)) {
			throw new ServiceExceptionWithStatusCode(
					"CodeSystem %s has no default module.".formatted(codeSystem.getShortName()),
					HttpStatus.BAD_REQUEST);
		}
		return moduleId;
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
