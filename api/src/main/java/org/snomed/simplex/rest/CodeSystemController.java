package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemClassificationStatus;
import org.snomed.simplex.client.domain.CodeSystemValidationStatus;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.domain.PackageConfiguration;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.snomed.simplex.rest.pojos.CreateCodeSystemRequest;
import org.snomed.simplex.rest.pojos.SetBranchRequest;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.snomed.simplex.service.validation.ValidationFixList;
import org.snomed.simplex.service.validation.ValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.snomed.simplex.domain.activity.ActivityType.*;
import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@RestController
@RequestMapping("api/codesystems")
@Tag(name = "Code Systems", description = "-")
public class CodeSystemController {

	private final SnowstormClientFactory clientFactory;

	private final CodeSystemService codeSystemService;

	private final JobService jobService;

	private final ValidationServiceClient validationServiceClient;

	private final ValidationService validationService;

	private final ReleaseServiceClient releaseServiceClient;

	private final ActivityService activityService;

	public CodeSystemController(SnowstormClientFactory clientFactory, CodeSystemService codeSystemService, JobService jobService,
			ValidationServiceClient validationServiceClient, ValidationService validationService,
			ReleaseServiceClient releaseServiceClient, ActivityService activityService) {
		this.clientFactory = clientFactory;
		this.codeSystemService = codeSystemService;
		this.jobService = jobService;
		this.validationServiceClient = validationServiceClient;
		this.validationService = validationService;
		this.releaseServiceClient = releaseServiceClient;
		this.activityService = activityService;
	}

	@GetMapping
	public Page<CodeSystem> getCodeSystems(@RequestParam(required = false, defaultValue = "false") boolean includeDetails) throws ServiceException {
		return new Page<>(codeSystemService.getCodeSystems(includeDetails));
	}

	@GetMapping("{codeSystem}")
	@PostAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public CodeSystem getCodeSystemDetails(@PathVariable String codeSystem) throws ServiceException {
		return codeSystemService.getCodeSystemDetails(codeSystem);
	}

	@Operation(description = """
			`dependantCodeSystem` is optional, by default the International Edition is used.
			
			`dependantCodeSystemVersion` is optional, by default the latest version is used.
			""")
	@PostMapping
	@PreAuthorize("hasPermission('ADMIN', '')")
	public CodeSystem createCodeSystem(@RequestBody CreateCodeSystemRequest request) throws ServiceException {
		return activityService.recordActivity(request.getShortName(), CODE_SYSTEM, CREATE, () ->
				codeSystemService.createCodeSystem(request)
		);
	}

	@PostMapping("{codeSystem}/upgrade")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob startUpgrade(@PathVariable String codeSystem, @RequestBody CodeSystemUpgradeRequest upgradeRequest) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return jobService.startExternalServiceJob(theCodeSystem, ActivityType.UPGRADE, job -> codeSystemService.upgradeCodeSystem(job, upgradeRequest));
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

		return jobService.startExternalServiceJob(theCodeSystem, ActivityType.CLASSIFY, codeSystemService::classify);
	}

	@PostMapping("{codeSystem}/validate")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob startValidation(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return jobService.startExternalServiceJob(theCodeSystem, ActivityType.VALIDATE, codeSystemService::validate);
	}

	@PostMapping("{codeSystem}/validate/run-automatic-fixes")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void runAutomaticFixes(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, AUTOMATIC_FIX, () -> {
			validationService.runAutomaticFixes(theCodeSystem);
			return null;
		});
	}

	@GetMapping("{codeSystem}/validate/issues")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public ValidationFixList getValidationFixList(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ValidationReport validationReport = getCompletedValidationReportOrThrow(theCodeSystem);
		return validationService.getValidationFixList(validationReport);
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
							HttpStatus.CONFLICT.value());
		}
		String validationReportUrl;
		if (validationJob != null) {
			validationReportUrl = validationJob.getLink();
		} else {
			validationReportUrl = theCodeSystem.getLatestValidationReport();
		}
		return validationServiceClient.getValidation(validationReportUrl);
	}

	@GetMapping(path = "{codeSystem}/product-packaging")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@Operation(summary = "Get generated Release Service product packaging for a code system. ",
			description = """
					Get the latest generated Release Service product packaging for a code system.


					This endpoint also saves the generated configuration.""")
	public ReleaseServiceClient.Product getReleaseProduct(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		PackageConfiguration packageConfiguration = codeSystemService.getPackageConfiguration(theCodeSystem.getBranchObject());
		return releaseServiceClient.getCreateProduct(theCodeSystem, packageConfiguration);
	}

	@GetMapping(path = "{codeSystem}/product-packaging/configuration")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@Operation(summary = "View the user configurable product packaging items for a code system. ",
			description = """
					View the user configurable product packaging items for a code system.


					The organisation name and contact details are required.""")
	public PackageConfiguration getReleaseProductConfig(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return codeSystemService.getPackageConfiguration(theCodeSystem.getBranchObject());
	}

	@PutMapping(path = "{codeSystem}/product-packaging/configuration")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void updateReleaseProductConfig(
			@PathVariable String codeSystem,
			@RequestBody PackageConfiguration packageConfiguration) throws ServiceException {

		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem codeSystemObject = snowstormClient.getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, UPDATE_CONFIGURATION, () -> {
			codeSystemService.updatePackageConfiguration(packageConfiguration, codeSystemObject.getBranchPath());
			releaseServiceClient.updateProductConfiguration(codeSystemObject, packageConfiguration);
			return null;
		});
	}

	@PostMapping(path = "{codeSystem}/product-packaging/build")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@Operation(summary = "Start a Release Service build for a code system. ",
			description = """
					This operation regenerates the Release Service product manifest, uploads the latest delta files,
					and creates and starts a Release Service build.
					""")
	public void buildReleaseProduct(@PathVariable String codeSystem, @RequestParam(required = false) String effectiveTime)
			throws ServiceException {

		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		if (effectiveTime == null) {
			effectiveTime = new SimpleDateFormat("yyyyMMdd").format(new Date());
		}

		PackageConfiguration packageConfiguration = codeSystemService.getPackageConfiguration(theCodeSystem.getBranchObject());
		if (Strings.isBlank(packageConfiguration.orgName()) || Strings.isBlank(packageConfiguration.orgContactDetails())) {
			throw new ServiceExceptionWithStatusCode("Organisation name and contact details must be set before creating a build.", HttpStatus.CONFLICT);
		}


		String finalEffectiveTime = effectiveTime;
		activityService.recordActivity(codeSystem, CODE_SYSTEM, START_BUILD, () -> {
			// TODO: Convert to Simplex scheduled job.
			releaseServiceClient.buildProduct(theCodeSystem, finalEffectiveTime);
			return null;
		});
	}

	@DeleteMapping("{codeSystem}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void deleteCodeSystem(@PathVariable String codeSystem) throws ServiceException {
		activityService.recordActivity(codeSystem, CODE_SYSTEM, DELETE, () -> {
			codeSystemService.deleteCodeSystem(codeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/working-branch")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void setBranchOverride(@PathVariable String codeSystem, @RequestBody SetBranchRequest request) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, UPDATE_CONFIGURATION, () -> {
			snowstormClient.setCodeSystemWorkingBranch(theCodeSystem, request.getBranchPath());
			return null;
		});
	}

	@PostMapping("{codeSystem}/start-release-prep")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startReleasePrep(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = clientFactory.getClient().getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, START_RELEASE_PREP, () -> {
			codeSystemService.startReleasePrep(theCodeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/approve-content-for-release")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void approveContentChanges(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = clientFactory.getClient().getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, ADD_CONTENT_APPROVAL, () -> {
			codeSystemService.approveContentForRelease(theCodeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/start-authoring")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startAuthoring(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = clientFactory.getClient().getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, REMOVE_CONTENT_APPROVAL, () -> {
			codeSystemService.startAuthoring(theCodeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/start-maintenance")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startMaintenance(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = clientFactory.getClient().getCodeSystemOrThrow(codeSystem);
		activityService.recordActivity(codeSystem, CODE_SYSTEM, REMOVE_CONTENT_APPROVAL, () -> {
			codeSystemService.startMaintenance(theCodeSystem);
			return null;
		});
	}

}
