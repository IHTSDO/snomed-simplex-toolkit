package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemClassificationStatus;
import org.snomed.simplex.client.domain.CodeSystemValidationStatus;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CreateCodeSystemRequest;
import org.snomed.simplex.rest.pojos.SetBranchRequest;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.SecurityService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.snomed.simplex.service.validation.ValidationFixList;
import org.snomed.simplex.service.validation.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("api/codesystems")
@Tag(name = "Code Systems", description = "-")
public class CodeSystemController {

	@Autowired
	private SnowstormClientFactory clientFactory;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private JobService jobService;

	@Autowired
	private SecurityService securityService;

	@Autowired
	private ValidationServiceClient validationServiceClient;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private ReleaseServiceClient releaseServiceClient;

	@GetMapping
	public Page<CodeSystem> getCodeSystems(@RequestParam(required = false, defaultValue = "false") boolean includeDetails) throws ServiceException {
		List<CodeSystem> codeSystems = clientFactory.getClient().getCodeSystems(includeDetails);
		securityService.updateUserRolePermissionCache(codeSystems);
		// Filter out codesystems where the user has no role.
		codeSystems = codeSystems.stream().filter(codeSystem -> !codeSystem.getUserRoles().isEmpty()).toList();
		return new Page<>(codeSystems);
	}

	@GetMapping("{codeSystem}")
	@PostAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public CodeSystem getCodeSystemDetails(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem codeSystemForDisplay = snowstormClient.getCodeSystemForDisplay(codeSystem);
		codeSystemService.addClassificationStatus(codeSystemForDisplay);
		codeSystemService.addValidationStatus(codeSystemForDisplay);
		securityService.updateUserRolePermissionCache(Collections.singletonList(codeSystemForDisplay));
		return codeSystemForDisplay;
	}

	@PostMapping
	@PreAuthorize("hasPermission('ADMIN', '')")
	public CodeSystem createCodeSystem(@RequestBody CreateCodeSystemRequest request) throws ServiceException {
		return codeSystemService.createCodeSystem(request.getName(), request.getShortName(), request.getNamespace(), request.isCreateModule(), request.getModuleName(),
				request.getModuleId());
	}

	@PostMapping("{codeSystem}/classify")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob createClassificationJob(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		codeSystemService.addClassificationStatus(theCodeSystem);
        if (theCodeSystem.isClassified()) {
			throw new ServiceException("This codesystem is already classified.");
		} else if (theCodeSystem.getClassificationStatus() == CodeSystemClassificationStatus.IN_PROGRESS) {
			throw new ServiceException("Classification is already in progress.");
		}

		return jobService.startExternalServiceJob(theCodeSystem, "Classify", asyncJob -> codeSystemService.classify(asyncJob));
	}

	@PostMapping("{codeSystem}/validate")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob startValidation(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return jobService.startExternalServiceJob(theCodeSystem, "Validate", asyncJob -> codeSystemService.validate(asyncJob));
	}

	@GetMapping("{codeSystem}/validate/issues")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public ValidationFixList getValidationFixList(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ValidationReport validationReport = getCompletedValidationReportOrThrow(theCodeSystem);
		return validationService.getValidationFixList(codeSystem, validationReport);
	}

	@GetMapping(path = "{codeSystem}/validate/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@ApiResponse(responseCode = "200", description = "The latest validation report was ready and downloaded okay.")
	@ApiResponse(responseCode = "400", description = "The latest validation report is not ready for download.")
	public void downloadValidationReport(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ValidationReport validationReport = getCompletedValidationReportOrThrow(theCodeSystem);
		response.setHeader("Content-Disposition", "attachment; filename=\"Simplex_Validation_Report.xlsx\"");
		validationServiceClient.downloadLatestValidationAsSpreadsheet(
				theCodeSystem, snowstormClient, validationReport, response.getOutputStream());

	}

	private ValidationReport getCompletedValidationReportOrThrow(CodeSystem theCodeSystem) throws ServiceException {
		ExternalServiceJob validationJob = codeSystemService.getLatestValidationJob(theCodeSystem);
		codeSystemService.addValidationStatus(theCodeSystem, validationJob);
		CodeSystemValidationStatus validationStatus = theCodeSystem.getValidationStatus();
		switch (validationStatus) {
			case TODO, IN_PROGRESS, SYSTEM_ERROR ->
					throw new ServiceExceptionWithStatusCode("The latest validation report is not available.",
							HttpStatus.BAD_REQUEST.value());
		}
		String validationReportUrl;
		if (validationJob != null) {
			validationReportUrl = validationJob.getLink();
		} else {
			validationReportUrl = theCodeSystem.getLatestValidationReport();
		}
		return validationServiceClient.getValidation(validationReportUrl);
	}

	@GetMapping(path = "{codeSystem}/packaging-product")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public ReleaseServiceClient.Product getReleaseProduct(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return releaseServiceClient.getCreateProduct(theCodeSystem);
	}

	@PutMapping(path = "{codeSystem}/packaging-product/configuration")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public ReleaseServiceClient.Product updateReleaseProduct(
			@PathVariable String codeSystem,
			@RequestBody ReleaseServiceClient.ProductUpdateRequest configUpdate) throws ServiceException {

		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return releaseServiceClient.updateProductConfiguration(theCodeSystem, configUpdate);
	}

	@DeleteMapping("{codeSystem}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void deleteCodeSystem(@PathVariable String codeSystem) throws ServiceException {
		codeSystemService.deleteCodeSystem(codeSystem);
	}

	@PostMapping("{codeSystem}/working-branch")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void setBranchOverride(@PathVariable String codeSystem, @RequestBody SetBranchRequest request) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.setCodeSystemWorkingBranch(theCodeSystem, request.getBranchPath());
	}

	@PostMapping("{codeSystem}/start-release-prep")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startReleasePrep(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		codeSystemService.setPreparingReleaseFlag(theCodeSystem, true);
	}

	@PostMapping("{codeSystem}/stop-release-prep")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void stopReleasePrep(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		codeSystemService.setPreparingReleaseFlag(theCodeSystem, false);
	}

}
