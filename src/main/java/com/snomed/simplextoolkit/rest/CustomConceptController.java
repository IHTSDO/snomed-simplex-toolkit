package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.Branch;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.CustomConceptService;
import com.snomed.simplextoolkit.service.JobService;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@RestController
@Api(tags = "Custom Concepts", description = "-")
@RequestMapping("api/{codeSystem}/concepts")
public class CustomConceptController {

	@Autowired
	private CustomConceptService customConceptService;

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@Autowired
	private JobService jobService;

	@GetMapping(path = "/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public void downloadCustomConceptSpreadsheet(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		response.setHeader("Content-Disposition", "attachment; filename=\"CustomConcepts.xlsx\"");
		customConceptService.downloadSpreadsheet(theCodeSystem, snowstormClient, response.getOutputStream());
	}


	@PutMapping(path = "/spreadsheet", consumes = "multipart/form-data")
	public AsyncJob updateCustomConceptList(@PathVariable String codeSystem, @RequestParam MultipartFile file) throws ServiceException, IOException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		return jobService.queueContentJob(codeSystem, "Custom concept upload", file.getInputStream(), null,
				asyncJob -> customConceptService.uploadSpreadsheet(theCodeSystem, asyncJob.getInputStream(), snowstormClient, asyncJob));
	}

	@PostMapping("/show")
	@ApiOperation("Show custom concepts option. This sets the showCustomConcepts flag on the codesystem object.")
	public void showCustomConceptOption(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.upsertBranchMetadata(theCodeSystem.getBranchPath(), Map.of(Branch.SHOW_CUSTOM_CONCEPTS, "true"));
	}

	@PostMapping("/hide")
	@ApiOperation("Hide custom concepts option. This sets the showCustomConcepts flag on the codesystem object.")
	public void hideCustomConceptOption(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.upsertBranchMetadata(theCodeSystem.getBranchPath(), Map.of(Branch.SHOW_CUSTOM_CONCEPTS, "false"));
	}

}
