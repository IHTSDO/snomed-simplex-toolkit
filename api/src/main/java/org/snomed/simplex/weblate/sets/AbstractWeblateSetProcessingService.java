package org.snomed.simplex.weblate.sets;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.WeblateSetRepository;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.snomed.simplex.weblate.WeblateSetService.*;

public abstract class AbstractWeblateSetProcessingService {

	private final ProcessingContext processingContext;
	private final WeblateSetRepository weblateSetRepository;

	AbstractWeblateSetProcessingService(ProcessingContext processingContext) {
		this.processingContext = processingContext;
		weblateSetRepository = processingContext.weblateSetRepository();
	}

	void queueJob(WeblateTranslationSet translationSet, String jobType) throws ServiceException {
		queueJob(translationSet, jobType, null);
	}

	void queueJob(WeblateTranslationSet translationSet, String jobType, Object requestObject) throws ServiceException {
		String username = SecurityUtil.getUsername();
		processingContext.userIdToContextMap().put(username, SecurityContextHolder.getContext());


		Map<String, String> properties = new HashMap<>(Map.of(JOB_TYPE, jobType,
			JOB_MESSAGE_USERNAME, username,
			JOB_MESSAGE_ID, translationSet.getId()));
		if (requestObject != null) {
			String requestObjectJson;
			try {
				requestObjectJson = processingContext.objectMapper().writeValueAsString(requestObject);
			} catch (JsonProcessingException e) {
				throw new ServiceException("Failed to queue job. Request object serialisation failed.", e);
			}
			properties.put(REQUEST_OBJECT, requestObjectJson);
		}
		processingContext.jmsTemplate().convertAndSend(processingContext.processingQueueName(), properties);
	}

	public ProcessingContext getProcessingContext() {
		return processingContext;
	}

	protected void setProgress(WeblateTranslationSet translationSet, int percentage) {
		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		translationSet.setPercentageProcessed(Math.min(100, percentage));
		weblateSetRepository.save(translationSet);
	}

	protected void setProgressToComplete(WeblateTranslationSet translationSet) {
		translationSet.setPercentageProcessed(100);
		translationSet.setStatus(TranslationSetStatus.READY);
		weblateSetRepository.save(translationSet);
	}
}
