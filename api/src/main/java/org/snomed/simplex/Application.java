package org.snomed.simplex;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application implements CommandLineRunner {

	@Override
	public void run(String... args) {
		// Launch the Spring Boot app
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
