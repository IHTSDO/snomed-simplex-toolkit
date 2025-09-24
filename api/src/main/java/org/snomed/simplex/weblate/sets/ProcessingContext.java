package org.snomed.simplex.weblate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.weblate.WeblateClientFactory;
import org.snomed.simplex.weblate.WeblateSetRepository;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.context.SecurityContext;

import java.util.Map;

public record ProcessingContext(
	SnowstormClientFactory snowstormClientFactory, WeblateClientFactory weblateClientFactory,
	WeblateSetRepository weblateSetRepository, org.snomed.simplex.weblate.TranslationLLMService translationLLMService, Map<String, SecurityContext> userIdToContextMap,
	JmsTemplate jmsTemplate, String processingQueueName, ObjectMapper objectMapper) {
}
