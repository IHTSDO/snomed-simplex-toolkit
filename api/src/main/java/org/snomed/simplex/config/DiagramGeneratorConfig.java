package org.snomed.simplex.config;

import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiagramGeneratorConfig {

    @Value("${diagram-generator.url}")
    private String diagramGeneratorUrl;

    @Bean
    public SnomedDiagramGeneratorClient snomedDiagramGeneratorClient() {
        return new SnomedDiagramGeneratorClient(diagramGeneratorUrl);
    }
} 