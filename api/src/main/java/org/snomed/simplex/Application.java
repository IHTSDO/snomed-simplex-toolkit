package org.snomed.simplex;

import org.snomed.simplex.config.ApplicationConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableElasticsearchRepositories
public class Application extends ApplicationConfig implements CommandLineRunner {

	@Override
	public void run(String... args) {
		// Launch the Spring Boot app
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
