package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LlmUsageSummary;
import org.snomed.simplex.service.LlmUsagePeriod;
import org.snomed.simplex.service.LlmUsageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/admin/llm-usage")
@Tag(name = "LLM Usage", description = "Admin LLM token usage reporting")
public class LlmUsageController {

	private final LlmUsageService llmUsageService;

	public LlmUsageController(LlmUsageService llmUsageService) {
		this.llmUsageService = llmUsageService;
	}

	@GetMapping
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Get LLM token usage summary for a time period")
	public LlmUsageSummary getUsage(
			@RequestParam String period,
			@RequestParam(required = false) String codesystem,
			@RequestParam(required = false) String model) throws ServiceExceptionWithStatusCode {

		return llmUsageService.getSummary(LlmUsagePeriod.fromParam(period), codesystem, model);
	}
}
