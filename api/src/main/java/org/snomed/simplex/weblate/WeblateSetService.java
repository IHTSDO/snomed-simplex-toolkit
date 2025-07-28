package org.snomed.simplex.weblate;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.util.FileUtils;
import org.snomed.simplex.util.TimerUtil;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.snomed.simplex.weblate.domain.WeblateLabel;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
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

	private final WeblateSetRepository weblateSetRepository;
	private final WeblateClientFactory weblateClientFactory;
	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final SupportRegister supportRegister;

	private final JmsTemplate jmsTemplate;
	private final String jmsQueuePrefix;
	private final int labelBatchSize;

	private final Map<String, SecurityContext> translationSetUserIdToUserContextMap;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateSetService(WeblateSetRepository weblateSetRepository, WeblateClientFactory weblateClientFactory, SnowstormClientFactory snowstormClientFactory, TranslationService translationService, SupportRegister supportRegister,
			JmsTemplate jmsTemplate, @Value("${jms.queue.prefix}") String jmsQueuePrefix, @Value("${weblate.label.batch-size}") int labelBatchSize) {

		this.weblateSetRepository = weblateSetRepository;
		this.weblateClientFactory = weblateClientFactory;
		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.supportRegister = supportRegister;
		this.jmsTemplate = jmsTemplate;
		this.jmsQueuePrefix = jmsQueuePrefix;
		this.labelBatchSize = labelBatchSize;
		translationSetUserIdToUserContextMap = new HashMap<>();
	}

	public List<WeblateTranslationSet> findByCodeSystem(String codeSystem) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> list = weblateSetRepository.findByCodesystem(codeSystem);
		return processTranslationSets(list);
	}

	public List<WeblateTranslationSet> findByCodeSystemAndRefset(String codeSystem, String refsetId) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> list = weblateSetRepository.findByCodesystemAndRefset(codeSystem, refsetId);
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
		ServiceHelper.requiredParameter("branchPath", translationSet.getSelectionCodesystem());

		Optional<WeblateTranslationSet> optional = weblateSetRepository.findByCodesystemAndLabelAndRefset(codesystemShortName, translationSet.getLabel(), refsetId);
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
			throw new ServiceExceptionWithStatusCode("Translation does not exist in Weblate, " +
					"please start language initialisation job or wait for it to finish.", HttpStatus.CONFLICT);
		}

		translationSet.setStatus(TranslationSetStatus.INITIALISING);

		logger.info("Queueing Weblate Translation Set for creation {}/{}/{}", codesystemShortName, refsetId, translationSet.getLabel());
		weblateSetRepository.save(translationSet);
		String username = SecurityUtil.getUsername();
		translationSetUserIdToUserContextMap.put(username, SecurityContextHolder.getContext());

		jmsTemplate.convertAndSend(jmsQueuePrefix + ".translation-set.processing",
			Map.of(JOB_TYPE, JOB_TYPE_CREATE,
				JOB_MESSAGE_USERNAME, username,
				JOB_MESSAGE_ID, translationSet.getId()));
		return translationSet;
	}

	public void deleteSet(WeblateTranslationSet translationSet) {
		logger.info("Queueing Weblate Translation Set for deletion {}/{}/{}", translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());
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
	public void processTranslationSet(Map<String, String> jobMessage) {
		String username = jobMessage.get(JOB_MESSAGE_USERNAME);
		String translationSetId = jobMessage.get(JOB_MESSAGE_ID);
		String jobType = jobMessage.get(JOB_TYPE);
		Optional<WeblateTranslationSet> optional = weblateSetRepository.findById(translationSetId);
		if (optional.isEmpty()) {
			logger.info("Weblate set was deleted before being processed {}", translationSetId);
			return;
		}

		WeblateTranslationSet translationSet = optional.get();
		SecurityContextHolder.setContext(translationSetUserIdToUserContextMap.get(username));

		try {
			logger.info("Starting - {} translation Set: {}/{}/{}",
				jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

			WeblateClient weblateClient = weblateClientFactory.getClient();
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			if (jobType.equals(JOB_TYPE_CREATE)) {
				doCreateSet(translationSet, weblateClient, snowstormClient);
			} else if (jobType.equals(JOB_TYPE_DELETE)) {
				doDeleteSet(translationSet, weblateClient);
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
			throw new ServiceExceptionWithStatusCode("Failed to download translation file from Weblate.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
		try (FileInputStream fileInputStream = new FileInputStream(subsetFile)) {
			contentJob.addUpload(fileInputStream, "weblate-automatic-download.csv");
			return translationService.uploadTranslationAsWeblateCSV(true, contentJob);
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Translation upload step failed.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		} finally {
			FileUtils.deleteOrLogWarning(subsetFile);
		}
	}

	private void doCreateSet(WeblateTranslationSet translationSet, WeblateClient weblateClient, SnowstormClient snowstormClient) throws ServiceExceptionWithStatusCode {
		TimerUtil timerUtil = new TimerUtil("Adding label %s".formatted(translationSet.getLabel()));
		// Update status to processing
		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		weblateSetRepository.save(translationSet);

		SnowstormClient.ConceptIdStream conceptIdStream = snowstormClient.getConceptIdStream(translationSet.getSelectionCodesystem(), translationSet.getEcl());

		String code;
		String compositeLabel = translationSet.getCompositeLabel();

		WeblateLabel weblateLabel = weblateClient.getCreateLabel(WeblateClient.COMMON_PROJECT, compositeLabel);

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

	private void updateProcessingTotal(WeblateTranslationSet translationSet, int done, int total) {
		if (total == 0) {
			total++;
		}
		translationSet.setPercentageProcessed((int) (((float) done / (float) total) * 100));
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
}
