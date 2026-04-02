package org.snomed.simplex;

import org.snomed.simplex.config.ApplicationConfig;
import org.snomed.simplex.service.test.TestActivityRepository;
import org.snomed.simplex.service.test.TestSnolateSetRepository;
import org.snomed.simplex.service.test.TestWeblateSetRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@EntityScan(basePackages = "org.snomed.simplex.snolate.domain")
@EnableJpaRepositories(basePackages = "org.snomed.simplex.snolate.repository")
@SpringBootApplication(
		exclude = {
				ElasticsearchDataAutoConfiguration.class,
				ElasticsearchRepositoriesAutoConfiguration.class,
				ElasticsearchRestClientAutoConfiguration.class
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
	public TestSnolateSetRepository snolateSetRepository() {
		return new TestSnolateSetRepository();
	}

}
