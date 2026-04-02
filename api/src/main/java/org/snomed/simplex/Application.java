package org.snomed.simplex;

import org.snomed.simplex.config.ApplicationConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EntityScan(basePackages = "org.snomed.simplex.snolate.domain")
@EnableElasticsearchRepositories(basePackages = {"org.snomed.simplex.service", "org.snomed.simplex.weblate", "org.snomed.simplex.snolate.sets"})
@EnableJpaRepositories(basePackages = "org.snomed.simplex.snolate.repository")
public class Application extends ApplicationConfig implements CommandLineRunner {

	@Override
	public void run(String... args) {
		// Launch the Spring Boot app
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
