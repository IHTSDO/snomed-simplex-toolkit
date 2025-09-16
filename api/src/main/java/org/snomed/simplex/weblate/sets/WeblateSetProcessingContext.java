package org.snomed.simplex.weblate.sets;

import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.weblate.WeblateClientFactory;
import org.snomed.simplex.weblate.WeblateSetRepository;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.context.SecurityContext;

import java.util.Map;

public record WeblateSetProcessingContext(
	SnowstormClientFactory snowstormClientFactory, WeblateClientFactory weblateClientFactory,
	WeblateSetRepository weblateSetRepository, Map<String, SecurityContext> userIdToContextMap,
	JmsTemplate jmsTemplate, String jmsQueuePrefix, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
}
