package org.snomed.simplex.weblate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.AssignWorkRequest;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.util.FileUtils;
import org.snomed.simplex.util.TimerUtil;
import org.snomed.simplex.weblate.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Service
public class WeblateSetService {

	private static final String JOB_MESSAGE_USERNAME = "username";
	private static final String JOB_MESSAGE_ID = "id";
	private static final String JOB_TYPE = "type";
	public static final String JOB_TYPE_CREATE = "Create";
	public static final String JOB_TYPE_DELETE = "Delete";
	public static final int PERCENTAGE_PROCESSED_START = 5;
	public static final String JOB_TYPE_ASSIGN_WORK = "AssignWork";
	public static final String ASSIGN_WORK_REQUEST = "assignWorkRequest";

	private final WeblateSetRepository weblateSetRepository;
	private final WeblateClientFactory weblateClientFactory;
	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final SupportRegister supportRegister;

	private final JmsTemplate jmsTemplate;
	private final String jmsQueuePrefix;
	private final int labelBatchSize;

	private final Map<String, SecurityContext> translationSetUserIdToUserContextMap;
	private final ObjectMapper objectMapper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateSetService(WeblateSetRepository weblateSetRepository, WeblateClientFactory weblateClientFactory, SnowstormClientFactory snowstormClientFactory, TranslationService translationService, SupportRegister supportRegister,
			JmsTemplate jmsTemplate, @Value("${jms.queue.prefix}") String jmsQueuePrefix, @Value("${weblate.label.batch-size}") int labelBatchSize, ObjectMapper objectMapper) {

		this.weblateSetRepository = weblateSetRepository;
		this.weblateClientFactory = weblateClientFactory;
		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.supportRegister = supportRegister;
		this.jmsTemplate = jmsTemplate;
		this.jmsQueuePrefix = jmsQueuePrefix;
		this.labelBatchSize = labelBatchSize;
		translationSetUserIdToUserContextMap = new HashMap<>();
		this.objectMapper = objectMapper;
	}

	public List<WeblateTranslationSet> findByCodeSystem(String codeSystem) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> list = weblateSetRepository.findByCodesystemOrderByName(codeSystem);
		return processTranslationSets(list);
	}

	public List<WeblateTranslationSet> findByCodeSystemAndRefset(String codeSystem, String refsetId) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> list = weblateSetRepository.findByCodesystemAndRefsetOrderByName(codeSystem, refsetId);
		return processTranslationSets(list);
	}

	public WeblateTranslationSet findSubsetOrThrow(String codeSystem, String refsetId, String label) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> list = findByCodeSystemAndRefset(codeSystem, refsetId);
		Optional<WeblateTranslationSet> first = list.stream().filter(set -> set.getLabel().equals(label)).findFirst();
		if (first.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Translation set not found.", HttpStatus.NOT_FOUND);
		}
		return first.get();
	}

	private List<WeblateTranslationSet> processTranslationSets(List<WeblateTranslationSet> list) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> deleting = list.stream().filter(set -> set.getStatus() == TranslationSetStatus.DELETING).toList();
		List<WeblateTranslationSet> deleted = new ArrayList<>();
		if (!deleting.isEmpty()) {
			WeblateClient weblateClient = weblateClientFactory.getClient();
			for (WeblateTranslationSet set : deleting) {
				WeblateLabel label = weblateClient.getLabel(WeblateClient.COMMON_PROJECT, set.getCompositeLabel());
				if (label == null) {
					weblateSetRepository.delete(set);
					deleted.add(set);
				}
			}
			list = new ArrayList<>(list);
			list.removeAll(deleted);
		}

		String webUrl = weblateClientFactory.getApiUrl().replaceAll("/api/?$", "");
		list.stream()
			.filter(set -> set.getStatus() == TranslationSetStatus.READY)
			.forEach(set -> set.setWeblateLabelUrl("%s/translate/common/snomedct/%s/?q=label:\"%s\""
				.formatted(webUrl, set.getLanguageCodeWithRefsetId(), set.getCompositeLabel())));
		return list;
	}

	public WeblateTranslationSet createSet(WeblateTranslationSet translationSet) throws ServiceExceptionWithStatusCode {
		String codesystemShortName = translationSet.getCodesystem();
		ServiceHelper.requiredParameter("codesystem", codesystemShortName);
		ServiceHelper.requiredParameter("name", translationSet.getName());
		String refsetId = translationSet.getRefset();
		ServiceHelper.requiredParameter("refset", refsetId);
		ServiceHelper.requiredParameter("label", translationSet.getLabel());
		ServiceHelper.requiredParameter("ecl", translationSet.getEcl());
		ServiceHelper.requiredParameter("selectionCodesystem", translationSet.getSelectionCodesystem());

		Optional<WeblateTranslationSet> optional = weblateSetRepository.findByCodesystemAndLabelAndRefsetOrderByName(codesystemShortName, translationSet.getLabel(), refsetId);
		if (optional.isPresent()) {
			throw new ServiceExceptionWithStatusCode("A translation set with this label already exists.", HttpStatus.CONFLICT);
		}

		CodeSystem codeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(codesystemShortName);
		String languageCode = codeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset: " + refsetId, HttpStatus.NOT_FOUND);
		}
		translationSet.setLanguageCode(languageCode);

		WeblateClient weblateClient = weblateClientFactory.getClient();
		if (!weblateClient.isTranslationExistsSearchByLanguageRefset(translationSet.getLanguageCodeWithRefsetId())) {
			throw new ServiceExceptionWithStatusCode("Translation does not exist in Translation Tool, " +
					"please start language initialisation job or wait for it to finish.", HttpStatus.CONFLICT);
		}

		translationSet.setStatus(TranslationSetStatus.INITIALISING);
		translationSet.setPercentageProcessed(PERCENTAGE_PROCESSED_START);

		logger.info("Queueing Translation Tool Translation Set for creation {}/{}/{}", codesystemShortName, refsetId, translationSet.getLabel());
		weblateSetRepository.save(translationSet);
		String username = SecurityUtil.getUsername();
		translationSetUserIdToUserContextMap.put(username, SecurityContextHolder.getContext());

		jmsTemplate.convertAndSend(jmsQueuePrefix + ".translation-set.processing",
			Map.of(JOB_TYPE, JOB_TYPE_CREATE,
				JOB_MESSAGE_USERNAME, username,
				JOB_MESSAGE_ID, translationSet.getId()));
		return translationSet;
	}

	public WeblatePage<WeblateUnit> getSampleRows(WeblateTranslationSet translationSet, int pageSize) throws ServiceExceptionWithStatusCode {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		return weblateClient.getUnitPage(UnitQueryBuilder.of(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT)
			.languageCode(translationSet.getLanguageCodeWithRefsetId())
			.compositeLabel(translationSet.getCompositeLabel())
			.pageSize(pageSize));
	}

	public WeblateUnit getSampleRow(WeblateTranslationSet translationSet, String conceptId) throws ServiceExceptionWithStatusCode {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		WeblateUnit unit = weblateClient.getUnitForConceptId(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT, conceptId, translationSet.getLanguageCodeWithRefsetId());
		if (unit != null) {
			String compositeLabel = translationSet.getCompositeLabel();
			List<WeblateLabel> labels = unit.getLabels();
			if (labels != null && labels.stream().anyMatch(label -> compositeLabel.equals(label.name()))) {
				return unit;
			}
		}
		return null;
	}

	public int getStateCount(WeblateTranslationSet translationSet, String state) throws ServiceExceptionWithStatusCode {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		WeblatePage<WeblateUnit> unitPage = weblateClient.getUnitPage(UnitQueryBuilder.of(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT)
			.languageCode(translationSet.getLanguageCodeWithRefsetId())
			.compositeLabel(translationSet.getCompositeLabel())
			.state(state)
			.pageSize(1));
		return unitPage.count();
	}

	public int getChangedSinceCount(WeblateTranslationSet translationSet) throws ServiceExceptionWithStatusCode {
		// Use the more recent of created or lastPulled as the baseline
		Date sinceDate = translationSet.getLastPulled() != null ?
			translationSet.getLastPulled() : translationSet.getCreated();

		if (sinceDate == null) {
			return 0; // No baseline date available
		}

		WeblateClient weblateClient = weblateClientFactory.getClient();
		// Use the optimized endpoint for much faster performance
		WeblatePage<WeblateUnit> unitPage = weblateClient.getUnitsWithChangesSince(
			WeblateClient.COMMON_PROJECT,
			WeblateClient.SNOMEDCT_COMPONENT,
			translationSet.getLanguageCodeWithRefsetId(),
			sinceDate,
			translationSet.getCompositeLabel(),
			null, // No state filter
			1 // Page size of 1 for counting
		);
		return unitPage.count();
	}

	public void updateSet(WeblateTranslationSet translationSet) {
		weblateSetRepository.save(translationSet);
	}

	public void deleteSet(WeblateTranslationSet translationSet) {
		logger.info("Queueing Translation Tool Translation Set for deletion {}/{}/{}", translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());
		translationSet.setStatus(TranslationSetStatus.DELETING);
		weblateSetRepository.save(translationSet);

		String username = SecurityUtil.getUsername();
		translationSetUserIdToUserContextMap.put(username, SecurityContextHolder.getContext());

		jmsTemplate.convertAndSend(jmsQueuePrefix + ".translation-set.processing",
			Map.of(JOB_TYPE, JOB_TYPE_DELETE,
				JOB_MESSAGE_USERNAME, username,
				JOB_MESSAGE_ID, translationSet.getId()));

	}

	@JmsListener(destination = "${jms.queue.prefix}.translation-set.processing", concurrency = "1")
	public void processTranslationSet(Map<String, Object> jobMessage) {
		String username = (String) jobMessage.get(JOB_MESSAGE_USERNAME);
		String translationSetId = (String) jobMessage.get(JOB_MESSAGE_ID);
		String jobType = (String) jobMessage.get(JOB_TYPE);
		Optional<WeblateTranslationSet> optional = weblateSetRepository.findById(translationSetId);
		if (optional.isEmpty()) {
			logger.info("Translation Tool set was deleted before being processed {}", translationSetId);
			return;
		}

		WeblateTranslationSet translationSet = optional.get();
		SecurityContextHolder.setContext(translationSetUserIdToUserContextMap.get(username));

		try {
			logger.info("Starting - {} translation Set: {}/{}/{}",
				jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

			WeblateClient weblateClient = weblateClientFactory.getClient();
			if (jobType.equals(JOB_TYPE_CREATE)) {
				doCreateSet(translationSet, weblateClient, snowstormClientFactory);
			} else if (jobType.equals(JOB_TYPE_DELETE)) {
				doDeleteSet(translationSet, weblateClient);
			} else if (jobType.equals(JOB_TYPE_ASSIGN_WORK)) {
				String requestJson = (String) jobMessage.get(ASSIGN_WORK_REQUEST);
				AssignWorkRequest request = objectMapper.readValue(requestJson, AssignWorkRequest.class);
				doAssignWorkToUsers(translationSet, request, weblateClient);
			} else {
				String errorMessage = "Unrecognised job type: %s, translationSet: %s, username: %s".formatted(jobType, translationSetId, username);
				supportRegister.handleSystemError(CodeSystem.SHARED, errorMessage, new ServiceException(errorMessage));
				return;
			}

			logger.info("Success - {} translation set: {}/{}/{}",
				jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

		} catch (Exception e) {
			logger.error("Error - {} translation set: {}/{}/{}",
				jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel(), e);

			// Update status to failed
			translationSet.setStatus(TranslationSetStatus.FAILED);
			weblateSetRepository.save(translationSet);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	public ChangeSummary pullTranslationSubset(ContentJob contentJob, String label) throws ServiceException {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		CodeSystem codeSystem = contentJob.getCodeSystemObject();
		WeblateTranslationSet translationSet = findSubsetOrThrow(codeSystem.getShortName(), contentJob.getRefsetId(), label);
		File subsetFile;
		try {
			subsetFile = weblateClient.downloadTranslationSubset(translationSet);
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to download translation file from Translation Tool.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
		try (FileInputStream fileInputStream = new FileInputStream(subsetFile)) {
			contentJob.addUpload(fileInputStream, "weblate-automatic-download.csv");
			ChangeSummary result = translationService.uploadTranslationAsWeblateCSV(true, contentJob);

			// Update the last pulled timestamp after successful pull
			translationSet.setLastPulled(new Date());
			weblateSetRepository.save(translationSet);

			return result;
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Translation upload step failed.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		} finally {
			FileUtils.deleteOrLogWarning(subsetFile);
		}
	}

	private void doCreateSet(WeblateTranslationSet translationSet, WeblateClient weblateClient, SnowstormClientFactory snowstormClientFactory) throws ServiceExceptionWithStatusCode {
		TimerUtil timerUtil = new TimerUtil("Adding label %s".formatted(translationSet.getLabel()));
		// Update status to processing
		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		weblateSetRepository.save(translationSet);

		SnowstormClient.ConceptIdStream conceptIdStream = getConceptIdStream(translationSet, snowstormClientFactory);

		String code;
		String compositeLabel = translationSet.getCompositeLabel();

		WeblateLabel weblateLabel = weblateClient.getCreateLabel(WeblateClient.COMMON_PROJECT, compositeLabel, translationSet.getName());

		List<String> codes = new ArrayList<>();
		int done = 0;
		while ((code = conceptIdStream.get()) != null) {
			codes.add(code);
			if (codes.size() == labelBatchSize) {
				bulkAddLabelsToBatch(compositeLabel, codes, weblateClient, weblateLabel);
				timerUtil.checkpoint("Completed batch");
				done += labelBatchSize;
				updateProcessingTotal(translationSet, done, conceptIdStream.getTotal());
			}
		}
		if (!codes.isEmpty()) {
			bulkAddLabelsToBatch(compositeLabel, codes, weblateClient, weblateLabel);
			updateProcessingTotal(translationSet, conceptIdStream.getTotal(), conceptIdStream.getTotal());
		}
		timerUtil.finish();

		translationSet.setStatus(TranslationSetStatus.READY);
		weblateSetRepository.save(translationSet);
	}

	private static SnowstormClient.ConceptIdStream getConceptIdStream(WeblateTranslationSet translationSet, SnowstormClientFactory snowstormClientFactory) throws ServiceExceptionWithStatusCode {
		String selectionCodesystemName = translationSet.getSelectionCodesystem();
		SnowstormClient snowstormClient;
		if (selectionCodesystemName.equals(SnowstormClientFactory.SNOMEDCT_DERIVATIVES)) {
			snowstormClient = snowstormClientFactory.getDerivativesClient();
		} else {
			snowstormClient = snowstormClientFactory.getClient();
		}
		CodeSystem selectionCodeSystem = snowstormClient.getCodeSystemOrThrow(selectionCodesystemName);
		return snowstormClient.getConceptIdStream(selectionCodeSystem.getBranchPath(), translationSet.getEcl());
	}

	private void updateProcessingTotal(WeblateTranslationSet translationSet, int done, int total) {
		if (total == 0) {
			total++;
		}
		translationSet.setPercentageProcessed((int) (((float) done / (float) total) * 100));
		translationSet.setSize(total);
		weblateSetRepository.save(translationSet);
	}

	private void doDeleteSet(WeblateTranslationSet translationSet, WeblateClient weblateClient) {
		weblateClient.deleteLabelAsync(WeblateClient.COMMON_PROJECT, translationSet.getCompositeLabel());
	}

	private void bulkAddLabelsToBatch(String label, List<String> codes, WeblateClient weblateClient, WeblateLabel weblateLabel) throws ServiceExceptionWithStatusCode {
		logger.info("Adding batch of label:{} to {} units", label, codes.size());
		weblateClient.bulkAddLabels(WeblateClient.COMMON_PROJECT, weblateLabel.id(), codes);
		logger.info("Added label batch");
		codes.clear();
	}

	public void assignWorkToUsers(String codeSystem, String refsetId, String label, AssignWorkRequest request) throws ServiceException {
		// Get the translation set to find the language code
		WeblateTranslationSet translationSet = findSubsetOrThrow(codeSystem, refsetId, label);

		// Set status to processing and save
		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		translationSet.setPercentageProcessed(PERCENTAGE_PROCESSED_START);
		weblateSetRepository.save(translationSet);

		String username = SecurityUtil.getUsername();
		translationSetUserIdToUserContextMap.put(username, SecurityContextHolder.getContext());

		try {
			String assignWorkRequestJson = objectMapper.writeValueAsString(request);
			// Queue the work assignment job
			jmsTemplate.convertAndSend(jmsQueuePrefix + ".translation-set.processing",
				Map.of(JOB_TYPE, JOB_TYPE_ASSIGN_WORK,
					JOB_MESSAGE_USERNAME, username,
					JOB_MESSAGE_ID, translationSet.getId(),
					ASSIGN_WORK_REQUEST, assignWorkRequestJson));
		} catch (JsonProcessingException e) {
			throw new ServiceException("Failed to queue assign job.", e);
		}
	}

	private void doAssignWorkToUsers(WeblateTranslationSet translationSet, AssignWorkRequest request, WeblateClient weblateClient) {
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
		Map<String, WeblateLabel> userLabels = createUserLabels(request, weblateClient);

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

				// Apply labels in bulk for each user
				for (Map.Entry<String, List<String>> entry : unitsByUser.entrySet()) {
					String username = entry.getKey();
					List<String> unitIds = entry.getValue();
					WeblateLabel labelObj = userLabels.get(username);

					if (labelObj != null && !unitIds.isEmpty()) {
						try {
							weblateClient.bulkAddLabels(WeblateClient.COMMON_PROJECT, labelObj.id(), unitIds);
							logger.debug("Bulk assigned {} units to user {}", unitIds.size(), username);
						} catch (ServiceExceptionWithStatusCode e) {
							logger.error("Failed to bulk assign units to user {}: {}", username, e.getMessage());
							// Fall back to individual assignments if bulk fails
							for (String unitId : unitIds) {
								try {
									List<WeblateLabel> currentLabels = new ArrayList<>();
									currentLabels.add(labelObj);
									weblateClient.patchUnitLabels(unitId, currentLabels);
								} catch (Exception ex) {
									logger.error("Failed to assign unit {} to user {}: {}", unitId, username, ex.getMessage());
								}
							}
						}
					}
				}

				// Update progress every page
				translationSet.setPercentageProcessed(Math.min(90, (int) (((float) processedUnits / (float) totalUnits) * 100)));
				weblateSetRepository.save(translationSet);
			}

		} while (unitsPage != null && unitsPage.next() != null);

		// Log final assignment summary
		for (WorkDistribution distribution : workDistribution) {
			logger.info("Assigned {} units ({}%) to user: {}", distribution.unitsAssigned, distribution.percentage, distribution.username);
		}

		// Final progress update
		translationSet.setPercentageProcessed(100);
		translationSet.setStatus(TranslationSetStatus.READY);
		weblateSetRepository.save(translationSet);

		logger.info("Work assignment completed for label: {}", label);
	}

	/**
	 * Pre-create and cache all user labels to avoid repeated lookups
	 */
	private Map<String, WeblateLabel> createUserLabels(AssignWorkRequest request, WeblateClient weblateClient) {
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
