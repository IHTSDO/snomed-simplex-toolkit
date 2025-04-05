package org.snomed.simplex.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.service.validation.ValidationTriageConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@Configuration
public abstract class ApplicationConfig {

	@Bean
	public ObjectMapper getGeneralMapper() {
		Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL);

		return builder.build();
	}

	@Bean
	@ConfigurationProperties(prefix = "validation")
	public ValidationTriageConfig getValidationTriageConfig() {
		return new ValidationTriageConfig();
	}

	@Bean(name = "indexNameProvider")
	public IndexNameProvider getIndexNameProvider(@Value("${elasticsearch.index.prefix}") String prefix) {
		return new IndexNameProvider(prefix);
	}

}
