package org.snomed.simplex.weblate.sets;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.snomed.simplex.weblate.WeblateSetService.*;

public abstract class WeblateSetProcessingService {

	private final WeblateSetProcessingContext processingContext;

	WeblateSetProcessingService(WeblateSetProcessingContext processingContext) {
		this.processingContext = processingContext;
	}

	void queueJob(WeblateTranslationSet translationSet, String jobType) {
		queueJob(translationSet, jobType, null);
	}

	void queueJob(WeblateTranslationSet translationSet, String jobType, Map<String, String> additionalProperties) {
		String username = SecurityUtil.getUsername();
		processingContext.userIdToContextMap().put(username, SecurityContextHolder.getContext());

		Map<String, String> properties = new HashMap<>(Map.of(JOB_TYPE, jobType,
			JOB_MESSAGE_USERNAME, username,
			JOB_MESSAGE_ID, translationSet.getId()));
		if (additionalProperties != null) {
			properties.putAll(additionalProperties);
		}
		processingContext.jmsTemplate().convertAndSend(processingContext.jmsQueuePrefix() + ".translation-set.processing", properties);
	}

	public WeblateSetProcessingContext getProcessingContext() {
		return processingContext;
	}
}
