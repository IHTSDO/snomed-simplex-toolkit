package org.snomed.simplex.weblate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.AssignWorkRequest;
import org.snomed.simplex.weblate.UnitQueryBuilder;
import org.snomed.simplex.weblate.WeblateAdminClient;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateSetRepository;
import org.snomed.simplex.weblate.domain.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.snomed.simplex.weblate.WeblateSetService.JOB_TYPE_ASSIGN_WORK;
import static org.snomed.simplex.weblate.WeblateSetService.PERCENTAGE_PROCESSED_START;

public class WeblateSetAssignService extends AbstractWeblateSetProcessingService {

	private final WeblateSetRepository weblateSetRepository;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateSetAssignService(ProcessingContext processingContext) {
		super(processingContext);
		weblateSetRepository = processingContext.weblateSetRepository();
	}

	public void assignWorkToUsers(WeblateTranslationSet translationSet, AssignWorkRequest request) throws ServiceException {
		// Set status to processing and save
		setProgress(translationSet, PERCENTAGE_PROCESSED_START);

		queueJob(translationSet, JOB_TYPE_ASSIGN_WORK, request);
	}

	public void doAssignWorkToUsers(WeblateTranslationSet translationSet, AssignWorkRequest request, WeblateClient weblateClient, WeblateAdminClient weblateAdminClient) throws ServiceExceptionWithStatusCode {
		String languageCodeWithRefset = translationSet.getLanguageCodeWithRefsetId();
		String label = translationSet.getCompositeLabel();

		// Get units with the specified label using query builder
		UnitQueryBuilder queryBuilder = UnitQueryBuilder.of(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT)
			.languageCode(languageCodeWithRefset)
			.compositeLabel(label);

		// First, get the total count without loading all units into memory
		WeblatePage<WeblateUnit> countPage = weblateClient.getUnitPage(queryBuilder.pageSize(1).fastestSort(true));
		int totalUnits = countPage != null ? countPage.count() : 0;

		logger.info("Found {} units with label: {}", totalUnits, label);

		if (totalUnits == 0) {
			logger.info("No units found with label: {}", label);
			translationSet.setStatus(TranslationSetStatus.READY);
			weblateSetRepository.save(translationSet);
			return;
		}

		// Calculate work distribution based on percentages
		List<WorkDistribution> workDistribution = calculateWorkDistribution(request, totalUnits);

		// Pre-create and cache all user labels to avoid repeated lookups
		Map<String, WeblateLabel> userLabels = createUserLabels(request, weblateAdminClient);

		// Process units page by page to minimize memory usage
		int processedUnits = 0;
		WeblatePage<WeblateUnit> unitsPage;
		int page = 1;
		do {
			// Get next page of units
			unitsPage = weblateClient.getUnitPage(queryBuilder.pageSize(1000).page(page++).fastestSort(true));

			if (unitsPage != null && unitsPage.results() != null) {
				// Group units by assigned user for bulk processing
				Map<String, List<String>> unitsByUser = new HashMap<>();

				// Process each unit in the current page to determine assignment
				for (WeblateUnit unit : unitsPage.results()) {
					// Find which user this unit should be assigned to
					WorkDistribution assignment = findAssignmentForUnit(workDistribution, processedUnits);

					if (assignment != null) {
						unitsByUser.computeIfAbsent(assignment.username, k -> new ArrayList<>()).add(unit.getContext());
					}

					processedUnits++;
				}

				doApplyLabels(weblateAdminClient, unitsByUser, userLabels);

				// Update progress every page
				setProgress(translationSet, Math.min(90, (int) (((float) processedUnits / (float) totalUnits) * 100)));
			}

		} while (unitsPage != null && unitsPage.next() != null);

		// Log final assignment summary
		for (WorkDistribution distribution : workDistribution) {
			logger.info("Assigned {} units ({}%) to user: {}", distribution.unitsAssigned, distribution.percentage, distribution.username);
		}

		// Final progress update
		setProgressToComplete(translationSet);
		logger.info("Work assignment completed for label: {}", label);
	}

	private void doApplyLabels(WeblateAdminClient weblateAdminClient, Map<String, List<String>> unitsByUser, Map<String, WeblateLabel> userLabels) throws ServiceExceptionWithStatusCode {
		// Apply labels in bulk for each user
		for (Map.Entry<String, List<String>> entry : unitsByUser.entrySet()) {
			String username = entry.getKey();
			List<String> unitIds = entry.getValue();
			WeblateLabel labelObj = userLabels.get(username);

			if (labelObj != null && !unitIds.isEmpty()) {
				weblateAdminClient.bulkAddLabels(WeblateClient.COMMON_PROJECT, labelObj.id(), unitIds);
				logger.debug("Bulk assigned {} units to user {}", unitIds.size(), username);
			}
		}
	}

	/**
	 * Pre-create and cache all user labels to avoid repeated lookups
	 */
	private Map<String, WeblateLabel> createUserLabels(AssignWorkRequest request, WeblateAdminClient weblateClient) {
		Map<String, WeblateLabel> userLabels = new HashMap<>();

		for (AssignWorkRequest.WorkAssignment assignment : request.getAssignments()) {
			String username = assignment.getUsername();
			String assignedLabel = "assigned-" + username;

			try {
				WeblateLabel labelObj = weblateClient.getCreateLabel(WeblateClient.COMMON_PROJECT, assignedLabel,
					"Work assigned to user: " + username);
				if (labelObj != null) {
					userLabels.put(username, labelObj);
					logger.debug("Created/cached label for user: {}", username);
				}
			} catch (Exception e) {
				logger.error("Failed to create label for user {}: {}", username, e.getMessage());
			}
		}

		return userLabels;
	}

	/**
	 * Calculate work distribution based on user percentages
	 */
	private List<WorkDistribution> calculateWorkDistribution(AssignWorkRequest request, int totalUnits) {
		List<WorkDistribution> distribution = new ArrayList<>();
		int remainingUnits = totalUnits;

		for (int i = 0; i < request.getAssignments().size(); i++) {
			AssignWorkRequest.WorkAssignment assignment = request.getAssignments().get(i);
			int percentage = assignment.getPercentage();

			// For the last assignment, give all remaining units to avoid rounding errors
			int unitsForUser;
			if (i == request.getAssignments().size() - 1) {
				unitsForUser = remainingUnits;
			} else {
				unitsForUser = (int) Math.round((percentage / 100.0) * totalUnits);
				remainingUnits -= unitsForUser;
			}

			distribution.add(new WorkDistribution(assignment.getUsername(), percentage, unitsForUser, 0));
		}

		return distribution;
	}

	/**
	 * Find which user a unit should be assigned to based on the current unit index
	 */
	private WorkDistribution findAssignmentForUnit(List<WorkDistribution> workDistribution, int unitIndex) {
		int currentIndex = 0;

		for (WorkDistribution distribution : workDistribution) {
			if (unitIndex < currentIndex + distribution.unitsToAssign) {
				distribution.unitsAssigned++;
				return distribution;
			}
			currentIndex += distribution.unitsToAssign;
		}

		return null; // Should not happen if distribution is calculated correctly
	}

	/**
	 * Helper class to track work distribution
	 */
	private static class WorkDistribution {
		final String username;
		final int percentage;
		final int unitsToAssign;
		int unitsAssigned;

		WorkDistribution(String username, int percentage, int unitsToAssign, int unitsAssigned) {
			this.username = username;
			this.percentage = percentage;
			this.unitsToAssign = unitsToAssign;
			this.unitsAssigned = unitsAssigned;
		}
	}

}
