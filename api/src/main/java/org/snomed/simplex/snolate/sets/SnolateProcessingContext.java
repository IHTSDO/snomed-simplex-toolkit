package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.snolate.repository.TranslationSourceRepository;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.context.SecurityContext;

import java.util.Map;

public record SnolateProcessingContext(
		SnowstormClientFactory snowstormClientFactory,
		SnolateSetRepository snolateSetRepository,
		TranslationSourceRepository translationSourceRepository,
		Map<String, SecurityContext> userIdToContextMap,
		JmsTemplate jmsTemplate,
		String processingQueueName,
		ObjectMapper objectMapper) {
}
