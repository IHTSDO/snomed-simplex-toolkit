package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.snolate.repository.TranslationSourceRepository;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.snomed.simplex.weblate.WeblateSetService.*;

@Service
public class SnolateSetService {

	private final SnolateSetRepository snolateSetRepository;
	private final SnowstormClientFactory snowstormClientFactory;
	private final SupportRegister supportRegister;
	private final SnolateSetCreationService creationService;

	private final Map<String, SecurityContext> userIdToContextMap;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnolateSetService(SnolateSetRepository snolateSetRepository, SnowstormClientFactory snowstormClientFactory,
			TranslationSourceRepository translationSourceRepository, SupportRegister supportRegister, JmsTemplate jmsTemplate,
			@Value("${jms.queue.prefix}") String jmsQueuePrefix, @Value("${snolate.label.batch-size}") int labelBatchSize, ObjectMapper objectMapper) {

		this.snolateSetRepository = snolateSetRepository;
		this.snowstormClientFactory = snowstormClientFactory;
		this.supportRegister = supportRegister;
		this.userIdToContextMap = new HashMap<>();

		String queueName = jmsQueuePrefix + ".snolate-translation-set.processing";
		SnolateProcessingContext processingContext = new SnolateProcessingContext(snowstormClientFactory, snolateSetRepository,
				translationSourceRepository, userIdToContextMap, jmsTemplate, queueName, objectMapper);
		creationService = new SnolateSetCreationService(processingContext, labelBatchSize);
	}

	public List<SnolateTranslationSet> findByCodeSystem(String codeSystem) {
		return snolateSetRepository.findByCodesystemOrderByName(codeSystem);
	}

	public List<SnolateTranslationSet> findByCodeSystemAndRefset(String codeSystem, String refsetId) {
		return snolateSetRepository.findByCodesystemAndRefsetOrderByName(codeSystem, refsetId);
	}

	public SnolateTranslationSet findSubsetOrThrow(String codeSystem, String refsetId, String label) throws ServiceExceptionWithStatusCode {
		List<SnolateTranslationSet> list = findByCodeSystemAndRefset(codeSystem, refsetId);
		Optional<SnolateTranslationSet> first = list.stream().filter(set -> set.getLabel().equals(label)).findFirst();
		if (first.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Snolate translation set not found.", HttpStatus.NOT_FOUND);
		}
		return first.get();
	}

	public SnolateTranslationSet createSet(SnolateTranslationSet translationSet) throws ServiceException {
		creationService.createSet(translationSet);
		return translationSet;
	}

	public SnolateTranslationSet refreshSet(SnolateTranslationSet translationSet) throws ServiceException {
		creationService.refreshSet(translationSet);
		return translationSet;
	}

	public void updateSet(SnolateTranslationSet translationSet) {
		snolateSetRepository.save(translationSet);
	}

	@JmsListener(destination = "${jms.queue.prefix}.snolate-translation-set.processing", concurrency = "1")
	public void processSnolateTranslationSet(Map<String, Object> jobMessage) {
		String username = (String) jobMessage.get(JOB_MESSAGE_USERNAME);
		String translationSetId = (String) jobMessage.get(JOB_MESSAGE_ID);
		String jobType = (String) jobMessage.get(JOB_TYPE);
		Optional<SnolateTranslationSet> optional = snolateSetRepository.findById(translationSetId);
		if (optional.isEmpty()) {
			logger.info("Snolate translation set was deleted before being processed {}", translationSetId);
			return;
		}

		SnolateTranslationSet translationSet = optional.get();
		SecurityContextHolder.setContext(userIdToContextMap.get(username));

		try {
			logger.info("Starting - {} Snolate translation set: {}/{}/{}",
					jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

			if (jobType.equals(JOB_TYPE_CREATE)) {
				creationService.doCreateSet(translationSet, snowstormClientFactory);
			} else if (jobType.equals(JOB_TYPE_REFRESH)) {
				creationService.doRefreshSet(translationSet, snowstormClientFactory);
			} else {
				String errorMessage = "Unrecognised Snolate job type: %s, translationSet: %s, username: %s".formatted(jobType, translationSetId, username);
				supportRegister.handleSystemError(CodeSystem.SHARED, errorMessage, new ServiceException(errorMessage));
				return;
			}

			logger.info("Success - {} Snolate translation set: {}/{}/{}",
					jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

		} catch (Exception e) {
			logger.error("Error - {} Snolate translation set: {}/{}/{}",
					jobType, translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel(), e);

			translationSet.setStatus(TranslationSetStatus.FAILED);
			snolateSetRepository.save(translationSet);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
