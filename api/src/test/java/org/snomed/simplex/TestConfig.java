package org.snomed.simplex;

import org.snomed.simplex.config.ApplicationConfig;
import org.snomed.simplex.service.test.TestActivityRepository;
import org.snomed.simplex.service.test.TestSnolateSetRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

@TestConfiguration
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
	public TestSnolateSetRepository snolateSetRepository() {
		return new TestSnolateSetRepository();
	}

	@Bean
	public SnolateTranslationUnitRepository snolateTranslationUnitRepository() {
		return mock(SnolateTranslationUnitRepository.class);
	}

	@Bean
	public SnolateTranslationSourceRepository snolateTranslationSourceRepository() {
		return mock(SnolateTranslationSourceRepository.class);
	}

	@Bean
	public SnolateTranslationSearchService snolateTranslationSearchService() {
		return mock(SnolateTranslationSearchService.class);
	}

}
