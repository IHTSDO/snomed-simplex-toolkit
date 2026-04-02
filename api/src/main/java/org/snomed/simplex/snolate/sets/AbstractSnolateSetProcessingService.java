package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.snomed.simplex.weblate.WeblateSetService.JOB_MESSAGE_ID;
import static org.snomed.simplex.weblate.WeblateSetService.JOB_MESSAGE_USERNAME;
import static org.snomed.simplex.weblate.WeblateSetService.JOB_TYPE;
import static org.snomed.simplex.weblate.WeblateSetService.REQUEST_OBJECT;

public abstract class AbstractSnolateSetProcessingService {

	private final SnolateProcessingContext processingContext;
	private final SnolateSetRepository snolateSetRepository;

	protected AbstractSnolateSetProcessingService(SnolateProcessingContext processingContext) {
		this.processingContext = processingContext;
		this.snolateSetRepository = processingContext.snolateSetRepository();
	}

	void queueJob(SnolateTranslationSet translationSet, String jobType) throws ServiceException {
		queueJob(translationSet, jobType, null);
	}

	void queueJob(SnolateTranslationSet translationSet, String jobType, Object requestObject) throws ServiceException {
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

	public SnolateProcessingContext getProcessingContext() {
		return processingContext;
	}

	protected void setProgress(SnolateTranslationSet translationSet, int percentage) {
		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		translationSet.setPercentageProcessed(Math.min(100, percentage));
		snolateSetRepository.save(translationSet);
	}

	protected void setProgressToComplete(SnolateTranslationSet translationSet) {
		translationSet.setPercentageProcessed(100);
		translationSet.setStatus(TranslationSetStatus.READY);
		snolateSetRepository.save(translationSet);
	}
}
