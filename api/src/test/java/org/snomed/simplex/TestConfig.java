package org.snomed.simplex;

import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.snomed.simplex.config.ApplicationConfig;
import org.snomed.simplex.service.test.TestActivityRepository;
import org.snomed.simplex.service.test.TestWeblateSetRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@TestConfiguration
@SpringBootApplication(
		exclude = {
				ElasticsearchDataAutoConfiguration.class,
				ElasticsearchRepositoriesAutoConfiguration.class,
				ElasticsearchRestClientAutoConfiguration.class,
				DataSourceAutoConfiguration.class
		})
public class TestConfig extends ApplicationConfig {

	@Bean
	public TestActivityRepository activityRepository() {
		return new TestActivityRepository();
	}

	@Bean
	public TestWeblateSetRepository weblateSetRepository() {
		return new TestWeblateSetRepository();
	}

	@Bean
	public SnomedDiagramGeneratorClient snomedDiagramGeneratorClient() {
		return new SnomedDiagramGeneratorClient("http://localhost:8082/");
	}
}
