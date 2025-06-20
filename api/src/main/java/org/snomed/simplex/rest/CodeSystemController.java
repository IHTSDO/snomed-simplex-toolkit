package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSProduct;
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
import org.snomed.simplex.service.external.*;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.snomed.simplex.service.validation.ValidationFixList;
import org.snomed.simplex.service.validation.ValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.snomed.simplex.domain.activity.ActivityType.*;
import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@RestController
@RequestMapping("api/codesystems")
@Tag(name = "Code Systems", description = "-")
public class CodeSystemController {

	private final SnowstormClientFactory clientFactory;
	private final CodeSystemService codeSystemService;
	private final ValidationService validationService;
	private final ValidateJobService validateJobService;
	private final ValidationServiceClient validationServiceClient;
	private final UpgradeJobService upgradeJobService;
	private final ActivityService activityService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystemController(SnowstormClientFactory clientFactory, CodeSystemService codeSystemService,
			ValidationService validationService, ValidateJobService validateJobService, ValidationServiceClient validationServiceClient,
			UpgradeJobService upgradeJobService, ActivityService activityService) {

		this.clientFactory = clientFactory;
		this.codeSystemService = codeSystemService;
		this.validationService = validationService;
		this.validateJobService = validateJobService;
		this.validationServiceClient = validationServiceClient;
		this.upgradeJobService = upgradeJobService;
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
			`name` will have words 'Edition' and 'Extension' removed then 'Edition' added at the end.

			`dependantCodeSystem` is optional, by default the International Edition is used.
			
			`dependantCodeSystemVersion` is optional, by default the latest version is used.
			""")
	@PostMapping
	@PreAuthorize("hasPermission('ADMIN', '')")
	public CodeSystem createCodeSystem(@RequestBody CreateCodeSystemRequest request) throws ServiceException {
		// Remove "edition" or "extension" from name
		String name = request.getName();
		name = name.replaceAll("(?i)\\s?\\b(edition|extension)\\b\\s?", " ").trim();
		request.setName("%s Edition".formatted(name));

		return activityService.runActivity(request.getShortName(), CODE_SYSTEM, CREATE, () ->
				codeSystemService.createCodeSystem(request)
		);
	}

	@PostMapping("{codeSystem}/upgrade")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob startUpgrade(@PathVariable String codeSystem, @RequestBody CodeSystemUpgradeRequest upgradeRequest) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return activityService.startExternalServiceActivity(theCodeSystem, CODE_SYSTEM, ActivityType.UPGRADE, upgradeJobService, upgradeRequest);
	}

	@PostMapping("{codeSystem}/classify")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob createClassificationJob(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		codeSystemService.addClassificationStatus(theCodeSystem);
		if (theCodeSystem.isClassified()) {
			throw new ServiceException("This codesystem is already classified.");
		} else if (theCodeSystem.getClassificationStatus() == CodeSystemClassificationStatus.IN_PROGRESS) {
			throw new ServiceException("Classification is already in progress.");
		}

		ClassifyJobService classifyService = codeSystemService.getClassifyJobService();
		return activityService.startExternalServiceActivity(theCodeSystem, CODE_SYSTEM, ActivityType.CLASSIFY, classifyService, null);
	}

	@PostMapping("{codeSystem}/validate")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob startValidation(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return activityService.startExternalServiceActivity(theCodeSystem, CODE_SYSTEM, ActivityType.VALIDATE, validateJobService, null);
	}

	@PostMapping("{codeSystem}/validate/run-automatic-fixes")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void runAutomaticFixes(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, AUTOMATIC_FIX, () -> {
			validationService.runAutomaticFixes(theCodeSystem);
			return null;
		});
	}

	@GetMapping("{codeSystem}/validate/issues")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public ValidationFixList getValidationFixList(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ValidationReport validationReport = getCompletedValidationReportOrThrow(theCodeSystem);
		return validationService.getValidationFixList(validationReport);
	}

	@GetMapping(path = "{codeSystem}/validate/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@ApiResponse(responseCode = "200", description = "The latest validation report was ready and downloaded okay.")
	@ApiResponse(responseCode = "400", description = "The latest validation report is not ready for download.")
	public void downloadValidationReport(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ValidationReport validationReport = getCompletedValidationReportOrThrow(theCodeSystem);
		response.setHeader("Content-Disposition", "attachment; filename=\"Simplex_Validation_Report.xlsx\"");
		validationServiceClient.downloadLatestValidationAsSpreadsheet(
				theCodeSystem, snowstormClient, validationReport, response.getOutputStream());
	}

	private ValidationReport getCompletedValidationReportOrThrow(CodeSystem theCodeSystem) throws ServiceException {
		validateJobService.addValidationStatus(theCodeSystem);
		CodeSystemValidationStatus validationStatus = theCodeSystem.getValidationStatus();
		if (Set.of(CodeSystemValidationStatus.TODO, CodeSystemValidationStatus.IN_PROGRESS, CodeSystemValidationStatus.SYSTEM_ERROR).contains(validationStatus)) {
					throw new ServiceExceptionWithStatusCode("The latest validation report is not available.", HttpStatus.CONFLICT);
		}
		String validationReportUrl;
		ExternalServiceJob latestJob = validateJobService.getLatestJob(theCodeSystem.getShortName());
		if (latestJob != null) {
			validationReportUrl = latestJob.getLink();
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
	public SRSProduct getReleaseProduct(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		PackageConfiguration packageConfiguration = codeSystemService.getPackageConfiguration(theCodeSystem.getBranchObject());
		ReleaseServiceClient releaseServiceClient = codeSystemService.getReleaseServiceClient();
		return releaseServiceClient.getCreateProduct(theCodeSystem, packageConfiguration);
	}

	@GetMapping(path = "{codeSystem}/product-packaging/configuration")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@Operation(summary = "View the user configurable product packaging items for a code system. ",
			description = """
					View the user configurable product packaging items for a code system.


					The organisation name and contact details are required.""")
	public PackageConfiguration getReleaseProductConfig(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return codeSystemService.getPackageConfiguration(theCodeSystem.getBranchObject());
	}

	@PutMapping(path = "{codeSystem}/product-packaging/configuration")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void updateReleaseProductConfig(
			@PathVariable String codeSystem,
			@RequestBody PackageConfiguration packageConfiguration) throws ServiceException {

		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem codeSystemObject = snowstormClient.getCodeSystemOrThrow(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, UPDATE_CONFIGURATION, () -> {
			codeSystemService.updatePackageConfiguration(packageConfiguration, codeSystemObject.getBranchPath());
			return null;
		});
	}

	@DeleteMapping("{codeSystem}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void deleteCodeSystem(@PathVariable String codeSystem) throws ServiceException {
		activityService.runActivity(codeSystem, CODE_SYSTEM, DELETE, () -> {
			codeSystemService.deleteCodeSystem(codeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/working-branch")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void setBranchOverride(@PathVariable String codeSystem, @RequestBody SetBranchRequest request) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, UPDATE_CONFIGURATION, () -> {
			snowstormClient.setCodeSystemWorkingBranch(theCodeSystem, request.getBranchPath());
			return null;
		});
	}

	@PostMapping("{codeSystem}/start-authoring")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startAuthoring(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, START_AUTHORING, () -> {
			codeSystemService.startAuthoring(theCodeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/start-release-prep")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startReleasePrep(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, START_RELEASE_PREP, () -> {
			codeSystemService.startReleasePrep(theCodeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/approve-content-for-release")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void approveContentChanges(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = getCodeSystemDetails(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, APPROVE_CONTENT, () -> {
			codeSystemService.approveContentForRelease(theCodeSystem);
			return null;
		});
	}

	@PostMapping("{codeSystem}/create-release-candidate")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	@Operation(summary = "Create a release candidate using the SNOMED Release Service. ",
			description = """
					This operation regenerates the Release Service product manifest, uploads the latest delta files,
					and creates and starts a Release Service build.
					The effectiveTime parameter (format yyyyMMdd) sets the effective time of the components and is included in the package name of the release candidate.
					If no effectiveTime is given it defaults to today's date.
					""")
	public void startReleaseBuild(@PathVariable String codeSystem, @RequestParam(required = false) String effectiveTime) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		if (effectiveTime == null) {
			effectiveTime = new SimpleDateFormat("yyyyMMdd").format(new Date());
		}

		if (theCodeSystem.getEditionStatus() != EditionStatus.RELEASE) {
			throw new ServiceExceptionWithStatusCode("CodeSystem must be approved for release before creating a release candidate.", HttpStatus.CONFLICT);
		}

		PackageConfiguration packageConfiguration = codeSystemService.getPackageConfiguration(theCodeSystem.getBranchObject());
		if (Strings.isBlank(packageConfiguration.orgName()) || Strings.isBlank(packageConfiguration.orgContactDetails())) {
			throw new ServiceExceptionWithStatusCode("Organisation name and contact details must be set before creating a build.", HttpStatus.CONFLICT);
		}
		ReleaseServiceClient releaseServiceClient = codeSystemService.getReleaseServiceClient();
		releaseServiceClient.getCreateProduct(theCodeSystem, packageConfiguration);

		ReleaseCandidateJobService releaseCandidateJobService = codeSystemService.getReleaseCandidateJobService();
		activityService.startExternalServiceActivity(theCodeSystem, CODE_SYSTEM, BUILD_RELEASE, releaseCandidateJobService, effectiveTime);
	}

	@GetMapping(path = "{codeSystem}/release-candidate", produces="application/zip")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void downloadReleaseCandidate(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		Pair<String, File> tempFile = codeSystemService.downloadReleaseCandidate(theCodeSystem);
		String filename = tempFile.getLeft().replace(".zip", "-release-candidate.zip");
		respondWithFile(response, filename, tempFile.getRight());
	}

	@Operation(summary = "Finalize the RF2 release.",
			description = """
					Publishes the latest release candidate as the final RF2 release. It also creates a CodeSystem version using the
					effective-time of the latest release candidate.
					The effective-time is a datestamp which must be unique therefore the maximum frequency of releases is one per day.
					""")
	@PostMapping("{codeSystem}/finalize-release")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void finalizeRelease(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = getCodeSystemDetails(codeSystem);
		// There is no job for this action. Publish status is updated when user views CodeSystem.
		PublishReleaseJobService publishReleaseJobService = codeSystemService.getPublishReleaseJobService();
		activityService.startExternalServiceActivity(theCodeSystem, CODE_SYSTEM, FINALIZE_RELEASE, publishReleaseJobService, null);
	}


	@PostMapping("{codeSystem}/start-maintenance")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void startMaintenance(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		activityService.runActivity(codeSystem, CODE_SYSTEM, START_MAINTENANCE, () -> {
			codeSystemService.startMaintenance(theCodeSystem);
			return null;
		});
	}

	@Operation(summary = "List CodeSystem versions that have completed publication.")
	@GetMapping("{codeSystem}/versions")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Page<CodeSystemVersion> listVersions(@PathVariable String codeSystem) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		return new Page<>(codeSystemService.getVersionsWithPackages(theCodeSystem));
	}

	@GetMapping("{codeSystem}/versions/{effectiveTime}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public CodeSystemVersion getVersion(@PathVariable String codeSystem, @PathVariable Integer effectiveTime) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		return doGetVersion(theCodeSystem, effectiveTime);
	}

	@GetMapping(path = "{codeSystem}/versions/{effectiveTime}/package", produces="application/zip")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void getVersionPackage(@PathVariable String codeSystem, @PathVariable Integer effectiveTime, HttpServletResponse response) throws ServiceException {
		CodeSystem theCodeSystem = getSnowstormClient().getCodeSystemOrThrow(codeSystem);
		CodeSystemVersion codeSystemVersion = doGetVersion(theCodeSystem, effectiveTime);
		Pair<String, File> packageNameAndFile = codeSystemService.downloadVersionPackage(theCodeSystem, codeSystemVersion);
		respondWithFile(response, packageNameAndFile.getLeft(), packageNameAndFile.getRight());
	}

	private CodeSystemVersion doGetVersion(CodeSystem codeSystem, Integer effectiveTime) throws ServiceException {
		List<CodeSystemVersion> versionsWithPackages = codeSystemService.getVersionsWithPackages(codeSystem);
		return versionsWithPackages.stream().filter(version -> effectiveTime.equals(version.effectiveDate())).findFirst().orElse(null);
	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return clientFactory.getClient();
	}

	private void respondWithFile(HttpServletResponse response, String filename, File file) {
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		try (FileInputStream fileInputStream = new FileInputStream(file)) {
			if (!response.isCommitted()) {
				StreamUtils.copy(fileInputStream, response.getOutputStream());
			}
		} catch (IOException e) {
			logger.info("Client hung up while downloading {}.", filename);
		} finally {
			if (!file.delete()) {
				logger.warn("Failed to delete temp download file {}", file.getAbsoluteFile());
			}
		}
	}
}
