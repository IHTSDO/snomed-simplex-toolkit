package org.snomed.simplex.config;

import org.snomed.simplex.service.validation.ValidationTriageConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeneralConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "validation")
    public ValidationTriageConfig getValidationTriageConfig() {
        return new ValidationTriageConfig();
    }

}
