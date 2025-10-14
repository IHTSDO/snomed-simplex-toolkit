package org.snomed.simplex.weblate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.AssignWorkRequest;
import org.snomed.simplex.rest.pojos.BatchTranslateRequest;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.util.FileUtils;
import org.snomed.simplex.weblate.domain.*;
import org.snomed.simplex.weblate.sets.BatchTranslationLLMService;
import org.snomed.simplex.weblate.sets.ProcessingContext;
import org.snomed.simplex.weblate.sets.WeblateSetAssignService;
import org.snomed.simplex.weblate.sets.WeblateSetCreationService;
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

	public static final String JOB_MESSAGE_USERNAME = "username";
	public static final String JOB_MESSAGE_ID = "id";
	public static final String JOB_TYPE = "type";
	public static final String JOB_TYPE_CREATE = "Create";
	public static final String JOB_TYPE_DELETE = "Delete";
	public static final int PERCENTAGE_PROCESSED_START = 5;
	public static final String JOB_TYPE_ASSIGN_WORK = "AssignWork";
	public static final String JOB_TYPE_BATCH_AI_TRANSLATE = "BatchAiTranslate";
	public static final String REQUEST_OBJECT = "requestObject";

	private final WeblateSetRepository weblateSetRepository;
	private final WeblateClientFactory weblateClientFactory;
	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final SupportRegister supportRegister;
	private final WeblateSetCreationService creationService;
	private final WeblateSetAssignService assignService;
	private final BatchTranslationLLMService batchTranslationLLMService;

	private final JmsTemplate jmsTemplate;
	private final String processingQueueName;

	private final Map<String, SecurityContext> userIdToContextMap;
	private final ObjectMapper objectMapper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateSetService(WeblateSetRepository weblateSetRepository, WeblateClientFactory weblateClientFactory, SnowstormClientFactory snowstormClientFactory,
			TranslationService translationService, SupportRegister supportRegister, TranslationLLMService translationLLMService,
			JmsTemplate jmsTemplate, @Value("${jms.queue.prefix}") String jmsQueuePrefix, @Value("${weblate.label.batch-size}") int labelBatchSize, ObjectMapper objectMapper) {

		this.weblateSetRepository = weblateSetRepository;
		this.weblateClientFactory = weblateClientFactory;
		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.supportRegister = supportRegister;
		this.jmsTemplate = jmsTemplate;
		userIdToContextMap = new HashMap<>();
		this.objectMapper = objectMapper;
		processingQueueName = jmsQueuePrefix + ".translation-set.processing";

		ProcessingContext processingContext = new ProcessingContext(snowstormClientFactory, weblateClientFactory,
			weblateSetRepository, translationLLMService, userIdToContextMap, jmsTemplate, processingQueueName, objectMapper);
		creationService = new WeblateSetCreationService(processingContext, labelBatchSize);
		assignService = new WeblateSetAssignService(processingContext);
		batchTranslationLLMService = new BatchTranslationLLMService(processingContext);
	}

	public List<WeblateTranslationSet> findByCodeSystem(String codeSystem) {
		List<WeblateTranslationSet> list = weblateSetRepository.findByCodesystemOrderByName(codeSystem);
		return filterAndCompleteSets(list);
	}

	public List<WeblateTranslationSet> findByCodeSystemAndRefset(String codeSystem, String refsetId) {
		List<WeblateTranslationSet> list = weblateSetRepository.findByCodesystemAndRefsetOrderByName(codeSystem, refsetId);
		return filterAndCompleteSets(list);
	}

	public WeblateTranslationSet findSubsetOrThrow(String codeSystem, String refsetId, String label) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> list = findByCodeSystemAndRefset(codeSystem, refsetId);
		Optional<WeblateTranslationSet> first = list.stream().filter(set -> set.getLabel().equals(label)).findFirst();
		if (first.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Translation set not found.", HttpStatus.NOT_FOUND);
		}
		return first.get();
	}

	private List<WeblateTranslationSet> filterAndCompleteSets(List<WeblateTranslationSet> list) {
		List<WeblateTranslationSet> deleting = list.stream().filter(set -> set.getStatus() == TranslationSetStatus.DELETING).toList();
		List<WeblateTranslationSet> deleted = new ArrayList<>();
		if (!deleting.isEmpty()) {
			WeblateAdminClient weblateAdminClient = weblateClientFactory.getAdminClient();
			for (WeblateTranslationSet set : deleting) {
				WeblateLabel label = weblateAdminClient.getLabel(WeblateClient.COMMON_PROJECT, set.getCompositeLabel());
				if (label == null) {
					weblateSetRepository.delete(set);
					deleted.add(set);
				}
			}
			list = new ArrayList<>(list);
			list.removeAll(deleted);
		}

		list.forEach(set ->	{
			boolean aiSetupComplete = false;
			Map<String, String> aiGoldenSet = set.getAiGoldenSet();
			if (aiGoldenSet != null && aiGoldenSet.size() >= 5 && aiGoldenSet.values().stream().noneMatch(Strings::isNullOrEmpty)) {
				aiSetupComplete = true;
			}
			set.setAiSetupComplete(aiSetupComplete);
		});


		String webUrl = weblateClientFactory.getApiUrl().replaceAll("/api/?$", "");
		list.stream()
			.filter(set -> set.getStatus() == TranslationSetStatus.READY)
			.forEach(set -> set.setWeblateLabelUrl("%s/translate/common/snomedct/%s/?q=label:\"%s\""
				.formatted(webUrl, set.getLanguageCodeWithRefsetId(), set.getCompositeLabel())));
		return list;
	}

	public WeblateTranslationSet createSet(WeblateTranslationSet translationSet) throws ServiceException {
		creationService.createSet(translationSet);
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

	public void runAiBatchTranslate(WeblateTranslationSet translationSet, BatchTranslateRequest request) throws ServiceException {
		batchTranslationLLMService.runAiBatchTranslate(translationSet, request);
	}

	public void deleteSet(WeblateTranslationSet translationSet) {
		logger.info("Queueing Translation Tool Translation Set for deletion {}/{}/{}", translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());
		translationSet.setStatus(TranslationSetStatus.DELETING);
		weblateSetRepository.save(translationSet);

		String username = SecurityUtil.getUsername();
		userIdToContextMap.put(username, SecurityContextHolder.getContext());

		jmsTemplate.convertAndSend(processingQueueName,
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
		SecurityContextHolder.setContext(userIdToContextMap.get(username));

		try {
			logger.info("Starting - {} translation Set: {}/{}/{}",
				jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

			WeblateClient weblateClient = weblateClientFactory.getClient();
			WeblateAdminClient weblateAdminClient = weblateClientFactory.getAdminClient();
			if (jobType.equals(JOB_TYPE_CREATE)) {
				creationService.doCreateSet(translationSet, weblateAdminClient, snowstormClientFactory);
			} else if (jobType.equals(JOB_TYPE_DELETE)) {
				doDeleteSet(translationSet, weblateAdminClient);
			} else if (jobType.equals(JOB_TYPE_ASSIGN_WORK)) {
				String requestJson = (String) jobMessage.get(REQUEST_OBJECT);
				AssignWorkRequest request = objectMapper.readValue(requestJson, AssignWorkRequest.class);
				assignService.doAssignWorkToUsers(translationSet, request, weblateClient, weblateAdminClient);
			} else if (jobType.equals(JOB_TYPE_BATCH_AI_TRANSLATE)) {
				String requestJson = (String) jobMessage.get(REQUEST_OBJECT);
				BatchTranslateRequest request = objectMapper.readValue(requestJson, BatchTranslateRequest.class);
				batchTranslationLLMService.doRunAiBatchTranslate(translationSet, request);
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

	private void doDeleteSet(WeblateTranslationSet translationSet, WeblateAdminClient weblateClient) {
		weblateClient.deleteLabelAsync(WeblateClient.COMMON_PROJECT, translationSet.getCompositeLabel());
	}

	public void assignWorkToUsers(String codeSystem, String refsetId, String label, AssignWorkRequest request) throws ServiceException {
		// Get the translation set to find the language code
		WeblateTranslationSet translationSet = findSubsetOrThrow(codeSystem, refsetId, label);
		assignService.assignWorkToUsers(translationSet, request);
	}
}
