package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/configuration")
@Tag(name = "Configuration Check", description = "-")
public class ConfigurationController {

	private final Environment environment;

	public ConfigurationController(Environment environment) {
		this.environment = environment;
	}

	@GetMapping
	@Operation(description = "Supports checking key configuration values over the API. Requires ADMIN role to view.")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public Map<String, String> getConfig() {
		Map<String, String> properties = new HashMap<>();
		addProperty("spring.application.name", properties);
		addProperty("snowstorm.url", properties);
		addProperty("snomed-release-service.url", properties);
		addProperty("snomed-release-service.username", properties);
		addProperty("ims-security.api-url", properties);
		addProperty("rvf.url", properties);
		addProperty("jms.queue.prefix", properties);
		return properties;
	}

	private void addProperty(String propName, Map<String, String> properties) {
		properties.put(propName, environment.getRequiredProperty(propName));
	}

}
