package com.snomed.derivativemanagementtool;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
public class Application implements CommandLineRunner {

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Starting SNOMED-CT derivative management process..");
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
