package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.CustomConceptService;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@Tag(name = "Custom Concepts", description = "-")
@RequestMapping("api/{codeSystem}/concepts")
public class CustomConceptController {

	private final CustomConceptService customConceptService;
	private final SnowstormClientFactory snowstormClientFactory;
	private final JobService jobService;

	public CustomConceptController(CustomConceptService customConceptService, SnowstormClientFactory snowstormClientFactory, JobService jobService) {
		this.customConceptService = customConceptService;
		this.snowstormClientFactory = snowstormClientFactory;
		this.jobService = jobService;
	}

	@GetMapping
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Page<ConceptMini> findAll(@PathVariable String codeSystem,
			@RequestParam(required = false, defaultValue = "0") int offset, @RequestParam(required = false, defaultValue = "100") int limit) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return customConceptService.findCustomConcepts(theCodeSystem, snowstormClient, offset, limit);
	}

	@GetMapping(path = "/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void downloadCustomConceptSpreadsheet(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		response.setHeader("Content-Disposition", "attachment; filename=\"CustomConcepts.xlsx\"");
		customConceptService.downloadSpreadsheet(theCodeSystem, snowstormClient, response.getOutputStream());
	}


	@PutMapping(path = "/spreadsheet", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob updateCustomConceptList(@PathVariable String codeSystem, @RequestParam MultipartFile file) throws ServiceException, IOException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		return jobService.queueContentJob(codeSystem, "Custom concept upload", file.getInputStream(), null,
				asyncJob -> customConceptService.uploadSpreadsheet(theCodeSystem, asyncJob.getInputStream(), snowstormClient, asyncJob));
	}

	@PostMapping("/show")
	@Operation(summary = "Show custom concepts option. This sets the showCustomConcepts flag on the codesystem object.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void showCustomConceptOption(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.upsertBranchMetadata(theCodeSystem.getBranchPath(), Map.of(Branch.SHOW_CUSTOM_CONCEPTS, "true"));
	}

	@PostMapping("/hide")
	@Operation(summary = "Hide custom concepts option. This sets the showCustomConcepts flag on the codesystem object.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void hideCustomConceptOption(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.upsertBranchMetadata(theCodeSystem.getBranchPath(), Map.of(Branch.SHOW_CUSTOM_CONCEPTS, "false"));
	}

}
