package org.snomed.simplex.service;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.config.VersionedPackagesResourceManagerConfiguration;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.PackageConfiguration;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.snomed.simplex.rest.pojos.CreateCodeSystemRequest;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Service
public class CodeSystemService {

	public static final String OWL_ONTOLOGY_REFSET = "762103008";
	public static final String OWL_EXPRESSION = "owlExpression";
	public static final String OWL_ONTOLOGY_HEADER = "734147008";
	public static final String CORE_METADATA_CONCEPT_TAG = "core metadata concept";
	public static final Pattern SHORT_NAME_PATTERN = Pattern.compile("^SNOMEDCT-[A-Z0-9_-]+$");
	public static final String SHORT_NAME_PREFIX = "SNOMEDCT-";

	private final SnowstormClientFactory snowstormClientFactory;
	private final JobService jobService;
	private final SupportRegister supportRegister;
	private final ValidationServiceClient validationServiceClient;
	private final ReleaseServiceClient releaseServiceClient;
	private final ActivityService activityService;
	private final SecurityService securityService;
	private final ResourceManager versionedPackagesResourceManager;

	private final Map<String, ExternalServiceJob> classificationJobsToMonitor = new HashMap<>();
	private final Map<String, ExternalServiceJob> validationJobsToMonitor = new HashMap<>();
	private final Map<String, ExternalServiceJob> releaseJobsToMonitor = new HashMap<>();
	private final Map<String, ExternalServiceJob> upgradeJobsToMonitor = new HashMap<>();

	@Value("${simplex.short-name.max-length:70}")
	private int maxShortNameLength;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystemService(SnowstormClientFactory snowstormClientFactory, JobService jobService, SupportRegister supportRegister,
			ValidationServiceClient validationServiceClient, ReleaseServiceClient releaseServiceClient, ActivityService activityService,
			VersionedPackagesResourceManagerConfiguration resourceManagerConfiguration, ResourceLoader resourceLoader,
			SecurityService securityService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.jobService = jobService;
		this.supportRegister = supportRegister;
		this.validationServiceClient = validationServiceClient;
		this.releaseServiceClient = releaseServiceClient;
		this.activityService = activityService;
		this.versionedPackagesResourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);
		this.securityService = securityService;
	}

	public List<CodeSystem> getCodeSystems(boolean includeDetails) throws ServiceException {
		List<CodeSystem> codeSystems = snowstormClientFactory.getClient().getCodeSystems(includeDetails);
		securityService.updateUserRolePermissionCache(codeSystems);
		// Filter out codesystems where the user has no role.
		codeSystems = codeSystems.stream().filter(codeSystem -> !codeSystem.getUserRoles().isEmpty()).toList();
		return codeSystems;
	}

	public CodeSystem getCodeSystemDetails(String codeSystemShortName) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem codeSystem = snowstormClient.getCodeSystemForDisplay(codeSystemShortName);
		addClassificationStatus(codeSystem);
		addValidationStatus(codeSystem);
		securityService.updateUserRolePermissionCache(Collections.singletonList(codeSystem));

		runStatusChecks(codeSystem, snowstormClient);

		// If publishing, check SRS publish status
		if (codeSystem.getEditionStatus() == EditionStatus.PUBLISHING) {
			String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
			SRSBuild build = releaseServiceClient.getBuild(buildUrl);
			if (build.getTags().contains("PUBLISHED")) {
				synchronized (this) {
					// After acquiring the lock, is status still publishing?
					codeSystem = snowstormClient.getCodeSystemForDisplay(codeSystemShortName);
					if (codeSystem.getEditionStatus() == EditionStatus.PUBLISHING) {
						String effectiveTime = build.configuration().getEffectiveTime();
						String releasePackageFilename = releaseServiceClient.getReleasePackageFilename(buildUrl);
						snowstormClient.setVersionReleasePackage(codeSystem, effectiveTime, releasePackageFilename);

						setEditionStatus(codeSystem, EditionStatus.AUTHORING, snowstormClient);
						clearBuildStatus(codeSystem, snowstormClient);
						Activity activity = new Activity(Activity.AUTOMATIC, codeSystemShortName, ComponentType.CODE_SYSTEM, ActivityType.START_AUTHORING);
						activityService.recordCompletedActivity(activity);
					}
				}
			}
		}

		return codeSystem;
	}

	private void runStatusChecks(CodeSystem codeSystem, SnowstormClient snowstormClient) {
		if (codeSystem.getBuildStatus() == CodeSystemBuildStatus.IN_PROGRESS) {
			String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
			if (buildUrl == null || !releaseJobsToMonitor.containsKey(buildUrl)) {
				logger.info("Reseting build status of {}", codeSystem.getShortName());
				clearBuildStatus(codeSystem, snowstormClient);
			}
		}
	}

	public CodeSystem createCodeSystem(CreateCodeSystemRequest createCodeSystemRequest) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		validateCreateRequest(createCodeSystemRequest);

		String dependantCodeSystemShortname = createCodeSystemRequest.getDependantCodeSystem();
		if (dependantCodeSystemShortname == null) {
			dependantCodeSystemShortname = "SNOMEDCT";
		}
		CodeSystem dependantCodeSystem = snowstormClient.getCodeSystemOrThrow(dependantCodeSystemShortname);

		// Create code system
		CodeSystem newCodeSystem = snowstormClient.createCodeSystem(
				createCodeSystemRequest.getName(), createCodeSystemRequest.getShortName(), createCodeSystemRequest.getNamespace(),
				dependantCodeSystem, createCodeSystemRequest.getDependantCodeSystemVersion());

		String existingModuleId = createCodeSystemRequest.getModuleId();
		String moduleId = existingModuleId;
		if (createCodeSystemRequest.isCreateModule()) {
			// Create module
			String moduleName = createCodeSystemRequest.getModuleName();
			Concept tempModuleConcept = snowstormClient.createSimpleMetadataConcept(Concepts.MODULE, moduleName, CORE_METADATA_CONCEPT_TAG, newCodeSystem);
			moduleId = tempModuleConcept.getConceptId();
			// Delete concept
			snowstormClient.deleteConcept(tempModuleConcept.getConceptId(), newCodeSystem);

			// Set default module on branch
			setDefaultModule(moduleId, newCodeSystem, snowstormClient);

			// Recreate
			Concept moduleConcept = snowstormClient.newSimpleMetadataConceptWithoutSave(Concepts.MODULE, moduleName, CORE_METADATA_CONCEPT_TAG);
			moduleConcept.setConceptId(moduleId);
			snowstormClient.createConcept(moduleConcept, newCodeSystem);
		} else if (existingModuleId != null && !existingModuleId.isEmpty()) {
			setDefaultModule(existingModuleId, newCodeSystem, snowstormClient);
		}

		createModuleOntologyExpression(moduleId, newCodeSystem, snowstormClient);

		String userGroupName = getUserGroupName(createCodeSystemRequest.getShortName());
		snowstormClient.setAuthorPermissions(newCodeSystem, userGroupName);

		return newCodeSystem;
	}

	public List<CodeSystemVersion> getVersionsWithPackages(CodeSystem theCodeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		List<CodeSystemVersion> versions = snowstormClient.getVersions(theCodeSystem);
		return versions.stream().filter(version -> version.releasePackage() != null).toList();
	}

	protected String getUserGroupName(String shortName) {
		String userGroupName = shortName.substring(SHORT_NAME_PREFIX.length()).toLowerCase();
		userGroupName = "simplex-%s-author".formatted(userGroupName);
		return userGroupName;
	}

	protected void validateCreateRequest(CreateCodeSystemRequest createCodeSystemRequest) throws ServiceExceptionWithStatusCode {
		String shortName = createCodeSystemRequest.getShortName();
		if (shortName == null || !shortName.startsWith(SHORT_NAME_PREFIX)) {
			throw new ServiceExceptionWithStatusCode("CodeSystem short name must start with 'SNOMEDCT-'", HttpStatus.BAD_REQUEST);
		}
		if (shortName.equals(SHORT_NAME_PREFIX)) {
			throw new ServiceExceptionWithStatusCode("CodeSystem short name must start with 'SNOMEDCT-' and contain other characters.", HttpStatus.BAD_REQUEST);
		}
		if (shortName.length() > maxShortNameLength) {
			throw new ServiceExceptionWithStatusCode(String.format("CodeSystem short name max length exceeded. " +
					"Maximum length is %s characters.", maxShortNameLength), HttpStatus.BAD_REQUEST);
		}
		boolean matches = SHORT_NAME_PATTERN.matcher(shortName).matches();
		if (!matches) {
			throw new ServiceExceptionWithStatusCode("CodeSystem short name can only contain characters A-Z, 0-9, hyphen and underscore.", HttpStatus.BAD_REQUEST);
		}
	}

	private static void setDefaultModule(String moduleId, CodeSystem newCodeSystem, SnowstormClient snowstormClient) {
		setCodeSystemMetadata(Branch.DEFAULT_MODULE_ID_METADATA_KEY, moduleId, newCodeSystem, snowstormClient);
	}

	public void startReleasePrep(CodeSystem codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		publishingStatusCheck(codeSystem);
		clearBuildStatus(codeSystem, snowstormClient);
		setEditionStatus(codeSystem, EditionStatus.PREPARING_RELEASE, snowstormClient);
	}

	private static void publishingStatusCheck(CodeSystem codeSystem) {
		if (codeSystem.getEditionStatus() == EditionStatus.PUBLISHING) {
			throw new IllegalStateException("Please wait for publishing to complete.");
		}
	}

	public void approveContentForRelease(CodeSystem codeSystem) throws ServiceException {
		publishingStatusCheck(codeSystem);

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		if (codeSystem.getEditionStatus() != EditionStatus.PREPARING_RELEASE) {
			throw new ServiceExceptionWithStatusCode("CodeSystem must be in 'Preparing Release' status first before approving content for release.", HttpStatus.CONFLICT);
		}
		if (!codeSystem.isClassified()) {
			throw new ServiceExceptionWithStatusCode("Content is not classified.", HttpStatus.CONFLICT);
		}
		CodeSystemValidationStatus validationStatus = codeSystem.getValidationStatus();
		if (validationStatus == CodeSystemValidationStatus.STALE) {
			throw new ServiceExceptionWithStatusCode("Validation is stale.", HttpStatus.CONFLICT);
		}
		if (!Set.of(CodeSystemValidationStatus.COMPLETE, CodeSystemValidationStatus.CONTENT_WARNING).contains(validationStatus)) {
			throw new ServiceExceptionWithStatusCode("Validation is not clean.", HttpStatus.CONFLICT);
		}
		clearBuildStatus(codeSystem, snowstormClient);
		setEditionStatus(codeSystem, EditionStatus.RELEASE, snowstormClient);
	}

	public void finalizeRelease(CodeSystem codeSystem) throws ServiceException {
		publishingStatusCheck(codeSystem);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		SRSBuild build = getReleaseCompleteBuildOrThrow(codeSystem);
		String effectiveTime = build.configuration().getEffectiveTime();

		setEditionStatus(codeSystem, EditionStatus.PUBLISHING, snowstormClient);

		logger.info("Versioning CodeSystem {}", codeSystem.getShortName());
		snowstormClient.versionCodeSystem(codeSystem, effectiveTime);

		logger.info("Publishing Release {}", build);
		releaseServiceClient.publishBuild(build);
	}

	public void startAuthoring(CodeSystem codeSystem) throws ServiceException {
		publishingStatusCheck(codeSystem);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		clearBuildStatus(codeSystem, snowstormClient);
		setEditionStatus(codeSystem, EditionStatus.AUTHORING, snowstormClient);
	}

	public void startMaintenance(CodeSystem codeSystem) throws ServiceException {
		publishingStatusCheck(codeSystem);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		clearBuildStatus(codeSystem, snowstormClient);
		setEditionStatus(codeSystem, EditionStatus.MAINTENANCE, snowstormClient);
	}

	private void clearBuildStatus(CodeSystem codeSystem, SnowstormClient snowstormClient) {
		CodeSystemBuildStatus newStatus = CodeSystemBuildStatus.TODO;
		codeSystem.setBuildStatus(newStatus);
		setCodeSystemMetadata(Branch.BUILD_STATUS_METADATA_KEY, newStatus.name(), codeSystem, snowstormClient);
	}

	private static void setCodeSystemMetadata(String key, String value, CodeSystem codeSystem, SnowstormClient snowstormClient) {
		Map<String, String> newMetadata = new HashMap<>();
		newMetadata.put(key, value);
		snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(), newMetadata);
	}

	private void createModuleOntologyExpression(String moduleId, CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		List<RefsetMember> ontologyMembers = snowstormClient.getRefsetMembers(OWL_ONTOLOGY_REFSET, codeSystem, false, 100, null).getItems();
		RefsetMember existingOntologyExpressionMember = null;
		for (RefsetMember ontologyMember : ontologyMembers) {
			if (ontologyMember.isActive() && ontologyMember.getAdditionalFields().get(OWL_EXPRESSION).startsWith("Ontology(<http://snomed.info/sct/")) {
				existingOntologyExpressionMember = ontologyMember;
			}
		}
		if (existingOntologyExpressionMember == null) {
			throw new ServiceException(format("Ontology expression is not found for code system %s", codeSystem.getShortName()));
		}
		String moduleOntologyExpression = format("Ontology(<http://snomed.info/sct/%s>)", moduleId);
		if (!existingOntologyExpressionMember.getAdditionalFields().get(OWL_EXPRESSION).equals(moduleOntologyExpression)) {
			existingOntologyExpressionMember.setActive(false);
			existingOntologyExpressionMember.setModuleId(moduleId);
			RefsetMember newOntologyExpressionMember = new RefsetMember(OWL_ONTOLOGY_REFSET, moduleId, OWL_ONTOLOGY_HEADER).setAdditionalField(OWL_EXPRESSION, moduleOntologyExpression);
			snowstormClient.createUpdateRefsetMembers(List.of(existingOntologyExpressionMember, newOntologyExpressionMember), codeSystem);
		}
	}

	private void setEditionStatus(CodeSystem codeSystem, EditionStatus editionStatus, SnowstormClient snowstormClient) {
		codeSystem.setEditionStatus(editionStatus);
		setCodeSystemMetadata(Branch.EDITION_STATUS_METADATA_KEY, codeSystem.getEditionStatus().name(), codeSystem, snowstormClient);
	}

	public void upgradeCodeSystem(ExternalServiceJob asyncJob, CodeSystemUpgradeRequest upgradeRequest) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();

			CodeSystem codeSystem = asyncJob.getCodeSystemObject();
			publishingStatusCheck(codeSystem);
			setEditionStatus(codeSystem, EditionStatus.MAINTENANCE, snowstormClient);
			// Disable daily-build to prevent content rollback during upgrade
			codeSystem.setDailyBuildAvailable(false);
			snowstormClient.updateCodeSystem(codeSystem);
			URI upgradeJobLocation = snowstormClient.createUpgradeJob(codeSystem, upgradeRequest);
			logger.info("Created upgrade job. Codesystem:{}, Snowstorm Job:{}", codeSystem.getShortName(), upgradeJobLocation);
			asyncJob.setLink(upgradeJobLocation.toString());
			upgradeJobsToMonitor.put(upgradeJobLocation.toString(), asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemError(asyncJob, "Failed to create upgrade job.", e);
		}
	}

	public void validate(ExternalServiceJob asyncJob) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			CodeSystem codeSystem = asyncJob.getCodeSystemObject();
			URI validationUri = validationServiceClient.startValidation(codeSystem, snowstormClient);
			logger.info("Created validation. Branch:{}, RVF Job:{}", codeSystem.getWorkingBranchPath(), validationUri);
			asyncJob.setLink(validationUri.toString());
			snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(), Map.of(Branch.LATEST_VALIDATION_REPORT_METADATA_KEY, validationUri.toString()));
			validationJobsToMonitor.put(validationUri.toString(), asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemError(asyncJob, "Failed to create validation job.", e);
		}
	}

	public void buildReleaseCandidate(String effectiveTime, ExternalServiceJob asyncJob) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(asyncJob.getCodeSystem());
			CodeSystem.CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
			if (latestVersion != null && Integer.parseInt(effectiveTime) <= latestVersion.effectiveDate()) {
				asyncJob.setStatus(JobStatus.USER_CONTENT_ERROR);
				asyncJob.setErrorMessage(("The latest version of this Code System is %s. " +
						"The effective-time date of the new release candidate must be after the latest version.").formatted(latestVersion.effectiveDate()));
				return;
			}

			SRSBuild releaseBuild = releaseServiceClient.buildProduct(codeSystem, effectiveTime);
			String releaseBuildUrl = releaseBuild.url();
			asyncJob.setLink(releaseBuildUrl);
			snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(),
					Map.of(Branch.LATEST_BUILD_METADATA_KEY, releaseBuildUrl,
							Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.IN_PROGRESS.name()));

			releaseJobsToMonitor.put(releaseBuildUrl, asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemError(asyncJob, "Failed to start release build job.", e);
		}
	}

	public void deleteCodeSystem(String shortName) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(shortName);
		// Delete code system including versions
		// Requires ADMIN permissions on codesystem branch
		snowstormClient.deleteCodeSystem(shortName);

		// Delete all branches
		// Requires ADMIN permissions on branches
		snowstormClient.deleteBranchAndChildren(codeSystem.getBranchPath());
	}

	public void classify(ExternalServiceJob asyncJob) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			String branch = asyncJob.getBranch();
			String classificationId = snowstormClient.createClassification(branch);
			logger.info("Started classification. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, asyncJob.getId());
			asyncJob.setLink(classificationId);
			classificationJobsToMonitor.put(classificationId, asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemError(asyncJob, "Failed to create classification.", e);
		}
	}

	@Scheduled(initialDelay = 15, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void monitorClassificationJobs() {
		Set<String> completedClassifications = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : classificationJobsToMonitor.entrySet()) {
			String classificationId = entry.getKey();
			ExternalServiceJob job = entry.getValue();
			String branch = job.getBranch();
			SecurityContextHolder.setContext(job.getSecurityContext());
			boolean classificationComplete = false;
			try {
				// Get client using the security context of the user who created the job
				SnowstormClient snowstormClient = snowstormClientFactory.getClient();
				SnowstormClassificationJob classificationJob = snowstormClient.getClassificationJob(branch, classificationId);
				SnowstormClassificationJob.Status status = classificationJob.getStatus();
				if (status == SnowstormClassificationJob.Status.COMPLETED) {
					if (!classificationJob.isEquivalentConceptsFound()) {
						// Start classification save (async process)
						snowstormClient.startClassificationSave(branch, classificationId);
						logger.info("Start classification save. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, job.getId());
					} else {
						supportRegister.handleTechnicalContentIssue(job, "Logically equivalent concepts have been found.");
						classificationComplete = true;
					}
				} else if (status == SnowstormClassificationJob.Status.SAVED) {
					logger.info("Classification saved. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, job.getId());
					job.setStatus(JobStatus.COMPLETE);
					classificationComplete = true;
				} else if (status == SnowstormClassificationJob.Status.FAILED) {
					supportRegister.handleSystemError(job, "Classification failed to run in Terminology Server.");
					classificationComplete = true;
				} else if (status == SnowstormClassificationJob.Status.SAVE_FAILED) {
					supportRegister.handleSystemError(job, "Classification failed to save in Terminology Server.");
					classificationComplete = true;
				} else if (status == SnowstormClassificationJob.Status.STALE) {
					logger.info("Classification stale. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, job.getId());
					job.setStatus(JobStatus.SYSTEM_ERROR);
					job.setErrorMessage("Classification became stale because of new content changes. Please try again.");
					classificationComplete = true;
				}
			} catch (ServiceException e) {
				supportRegister.handleSystemError(job, "Terminology Server API issue.", e);
				classificationComplete = true;
			}
			if (classificationComplete) {
				completedClassifications.add(classificationId);
			}
		}
		for (String completedClassification : completedClassifications) {
			classificationJobsToMonitor.remove(completedClassification);
		}
	}

	@Scheduled(initialDelay = 15, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void monitorValidationJobs() {
		Set<String> completedValidations = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : validationJobsToMonitor.entrySet()) {
			String validationUrl = entry.getKey();
			ExternalServiceJob job = entry.getValue();
			SecurityContextHolder.setContext(job.getSecurityContext());
			boolean validationComplete = false;
			try {
				// Get client using the security context of the user who created the job
				ValidationReport validationReport = validationServiceClient.getValidation(validationUrl);
				ValidationReport.State status = validationReport.status();
				logger.debug("Validation status {} for {}", status, validationUrl);
				if (status != null) {
					if (status == ValidationReport.State.COMPLETE) {
						logger.info("Validation completed. Branch:{}, RVF Job:{}, Status:{}", job.getBranch(), validationUrl, status);
						setValidationJobStatusAndMessage(job, validationReport);
						validationComplete = true;
					} else if (status == ValidationReport.State.FAILED) {
						supportRegister.handleSystemError(job, "RVF report failed.");
						validationComplete = true;
					}
				}
			} catch (ServiceException e) {
				supportRegister.handleSystemError(job, "Terminology Server or RVF API issue.", e);
				validationComplete = true;
			}
			if (validationComplete) {
				completedValidations.add(validationUrl);
			}
		}
		for (String completedValidation : completedValidations) {
			validationJobsToMonitor.remove(completedValidation);
		}
	}

	@Scheduled(initialDelay = 15, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void monitorUpgradeJobs() {
		Set<String> completedUpgrades = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : upgradeJobsToMonitor.entrySet()) {
			String upgradeLocation = entry.getKey();
			ExternalServiceJob job = entry.getValue();
			SecurityContextHolder.setContext(job.getSecurityContext());
			boolean upgradeComplete = false;
			try {
				// Get client using the security context of the user who created the job
				SnowstormClient snowstormClient = snowstormClientFactory.getClient();
				SnowstormUpgradeJob upgradeJob = snowstormClient.getUpgradeJob(upgradeLocation);
				SnowstormUpgradeJob.Status status = upgradeJob.getStatus();
				if (status == SnowstormUpgradeJob.Status.COMPLETED) {
					CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(job.getCodeSystem());
					codeSystem.setDailyBuildAvailable(true);
					snowstormClient.updateCodeSystem(codeSystem);
					logger.info("Upgrade complete. Codesystem:{}", codeSystem.getShortName());
					job.setStatus(JobStatus.COMPLETE);
					upgradeComplete = true;
				} else if (status == SnowstormUpgradeJob.Status.FAILED) {
					supportRegister.handleSystemError(job, "Upgrade failed to run in Terminology Server.");
					upgradeComplete = true;
				}
			} catch (ServiceException e) {
				supportRegister.handleSystemError(job, "Terminology Server API issue.", e);
				upgradeComplete = true;
			}
			if (upgradeComplete) {
				completedUpgrades.add(upgradeLocation);
			}
		}
		for (String completedJobs : completedUpgrades) {
			upgradeJobsToMonitor.remove(completedJobs);
		}
	}

	@Scheduled(initialDelay = 15, fixedDelay = 15, timeUnit = TimeUnit.SECONDS)
	public void monitorReleaseJobs() {
		Set<String> completedBuildJobs = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : releaseJobsToMonitor.entrySet()) {
			String buildUrl = entry.getKey();
			ExternalServiceJob job = entry.getValue();
			SecurityContextHolder.setContext(job.getSecurityContext());
			boolean buildComplete = false;
			CodeSystemBuildStatus buildStatus = null;
			try {
				// Get client using the security context of the user who created the job
				SRSBuild build = releaseServiceClient.getBuild(buildUrl);
				buildStatus = CodeSystemBuildStatus.fromSRSStatus(build.status());
				logger.debug("Build status {} for {}", buildStatus, buildUrl);

				switch (buildStatus) {
					case IN_PROGRESS:
						job.setStatus(JobStatus.IN_PROGRESS);
						break;
					case FAILED:
						supportRegister.handleSystemError(job, "SRS build failed.");
						job.setStatus(JobStatus.SYSTEM_ERROR);// We don't expect the build to fail
						buildComplete = true;
						break;
					case COMPLETE:
						logger.info("Build completed. Branch:{}, SRS Job:{}, Status:{}", job.getBranch(), buildUrl, buildStatus);
						job.setStatus(JobStatus.COMPLETE);
						buildComplete = true;
						break;
					default:
						logger.warn("Unexpected build status: {}, Branch:{}", buildStatus, job.getBranch());
						break;
				}
			} catch (ServiceException e) {
				supportRegister.handleSystemError(job, "SRS API issue.", e);
				buildComplete = true;
			}
			if (buildComplete) {
				completedBuildJobs.add(buildUrl);
				if (buildStatus != null) {
					try {
						String codeSystem = job.getCodeSystem();
						SnowstormClient snowstormClient = snowstormClientFactory.getClient();
						CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
						setCodeSystemMetadata(Branch.BUILD_STATUS_METADATA_KEY, buildStatus.name(), theCodeSystem, snowstormClient);
					} catch (ServiceException e) {
						supportRegister.handleSystemError(job, "Failed to update build status in branch metadata", e);
					}
				}
			}
		}
		for (String completedBuildJob : completedBuildJobs) {
			releaseJobsToMonitor.remove(completedBuildJob);
		}
	}

	private static void setValidationJobStatusAndMessage(ExternalServiceJob job, ValidationReport validationReport) {
		ValidationReport.ValidationResult validationResult = validationReport.rvfValidationResult();
		if (validationResult == null) {
			job.setStatus(JobStatus.IN_PROGRESS);
		} else {
			ValidationReport.TestResult testResult = validationResult.TestResult();
			if (testResult.totalFailures() > 0) {
				job.setErrorMessage("Validation errors were found in the content.");
				job.setStatus(JobStatus.USER_CONTENT_ERROR);
			} else if (testResult.totalWarnings() > 0) {
				job.setErrorMessage("Validation warnings were found in the content.");
				job.setStatus(JobStatus.USER_CONTENT_WARNING);
			} else {
				job.setStatus(JobStatus.COMPLETE);
			}
		}
	}

	public void addClassificationStatus(CodeSystem theCodeSystem) {
		CodeSystemClassificationStatus status;
		if (theCodeSystem.isClassified()) {
			status = CodeSystemClassificationStatus.COMPLETE;
		} else {
			status = CodeSystemClassificationStatus.TODO;

			AsyncJob latestJobOfType = jobService.getLatestJobOfType(theCodeSystem.getShortName(), "Classify");
			if (latestJobOfType != null) {
				switch (latestJobOfType.getStatus()) {
					case QUEUED, IN_PROGRESS ->
							status = CodeSystemClassificationStatus.IN_PROGRESS;
					case SYSTEM_ERROR ->
							status = CodeSystemClassificationStatus.SYSTEM_ERROR;
					case TECHNICAL_CONTENT_ISSUE ->
							status = CodeSystemClassificationStatus.EQUIVALENT_CONCEPTS;
					case USER_CONTENT_ERROR, USER_CONTENT_WARNING ->
							// Not expected
							status = CodeSystemClassificationStatus.SYSTEM_ERROR;
					case COMPLETE ->
							status = CodeSystemClassificationStatus.COMPLETE;
				}
			}
		}
		theCodeSystem.setClassificationStatus(status);
	}

	public void addValidationStatus(CodeSystem codeSystem) throws ServiceException {
		addValidationStatus(codeSystem, getLatestValidationJob(codeSystem));
	}

	public void addValidationStatus(CodeSystem codeSystem, ExternalServiceJob latestValidationJob) throws ServiceException {
		CodeSystemValidationStatus status = CodeSystemValidationStatus.TODO;
		if (latestValidationJob != null) {
			switch (latestValidationJob.getStatus()) {
				case QUEUED, IN_PROGRESS -> status = CodeSystemValidationStatus.IN_PROGRESS;
				case SYSTEM_ERROR -> status = CodeSystemValidationStatus.SYSTEM_ERROR;
				case TECHNICAL_CONTENT_ISSUE ->
					// Not expected
						status = CodeSystemValidationStatus.SYSTEM_ERROR;
				case USER_CONTENT_ERROR -> status = CodeSystemValidationStatus.CONTENT_ERROR;
				case USER_CONTENT_WARNING -> status = CodeSystemValidationStatus.CONTENT_WARNING;
				case COMPLETE -> status = CodeSystemValidationStatus.COMPLETE;
			}
			if (status.isCanTurnStale() && latestValidationJob.getContentHeadTimestamp() != codeSystem.getContentHeadTimestamp()) {
				logger.info("Validation report {} was {} is now stale.", latestValidationJob.getLink(), status);
				logger.debug("Validation report {} was {} is now stale, validationHead:{}, contentHead:{}.",
						latestValidationJob.getLink(), status, latestValidationJob.getContentHeadTimestamp(), codeSystem.getContentHeadTimestamp());

				status = CodeSystemValidationStatus.STALE;
			}
		} else {
			// Service may have been restarted. Attempt recovery of status using existing report.
			if (codeSystem.getLatestValidationReport() != null) {
				ValidationReport validationReport = validationServiceClient.getValidation(codeSystem.getLatestValidationReport());
				ExternalServiceJob tempJob = new ExternalServiceJob(codeSystem, "temp job");
				setValidationJobStatusAndMessage(tempJob, validationReport);
				if (tempJob.getStatus() == JobStatus.USER_CONTENT_ERROR) {
					status = CodeSystemValidationStatus.CONTENT_ERROR;
				} else if (tempJob.getStatus() == JobStatus.USER_CONTENT_WARNING) {
					status = CodeSystemValidationStatus.CONTENT_WARNING;
				} else if (tempJob.getStatus() == JobStatus.COMPLETE) {
					status = CodeSystemValidationStatus.COMPLETE;
				}
				ValidationReport.ValidationResult validationResult = validationReport.rvfValidationResult();
				if (validationResult != null) {
					long validationHead = validationResult.validationConfig().contentHeadTimestamp();
					if (status.isCanTurnStale() && validationHead != codeSystem.getContentHeadTimestamp()) {
						status = CodeSystemValidationStatus.STALE;
					}
				}
			}
		}
		codeSystem.setValidationStatus(status);
	}

	public ExternalServiceJob getLatestValidationJob(CodeSystem codeSystem) {
		return (ExternalServiceJob) jobService.getLatestJobOfType(codeSystem.getShortName(), ActivityType.VALIDATE.getDisplay());
	}

	public PackageConfiguration getPackageConfiguration(Branch branch) {
		String orgName = branch.getMetadataValue(Branch.ORGANISATION_NAME);
		String orgContactDetails = branch.getMetadataValue(Branch.ORGANISATION_CONTACT_DETAILS);
		return new PackageConfiguration(orgName, orgContactDetails);
	}

	public void updatePackageConfiguration(PackageConfiguration packageConfiguration, String branchPath) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		Map<String, String> metadataUpdate = Map.of(
				Branch.ORGANISATION_NAME, packageConfiguration.orgName(),
				Branch.ORGANISATION_CONTACT_DETAILS, packageConfiguration.orgContactDetails());
		snowstormClient.upsertBranchMetadata(branchPath, metadataUpdate);
	}

	public Pair<String, File> downloadReleaseCandidate(CodeSystem codeSystem) throws ServiceException {
		SRSBuild buildUrl = getReleaseCompleteBuildOrThrow(codeSystem);

		try {
			return releaseServiceClient.downloadReleaseCandidatePackage(buildUrl.url());
		} catch (ServiceException e) {
			String errorMessage = "Failed to download release package.";
			supportRegister.handleSystemError(codeSystem, errorMessage, e);
			throw new ServiceExceptionWithStatusCode(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public Pair<String, File> downloadVersionPackage(CodeSystem codeSystem, CodeSystemVersion codeSystemVersion) throws ServiceException {
		try {
			String filename = codeSystemVersion.releasePackage();
			File tempFile = File.createTempFile("release-download" + UUID.randomUUID(), "tmp");
			logger.info("Downloading versioned package {}", filename);
			try (InputStream inputStream = versionedPackagesResourceManager.readResourceStream(filename)) {
				Streams.copy(inputStream, new FileOutputStream(tempFile), true);
			}
			return Pair.of(filename, tempFile);
		} catch (FileNotFoundException e) {
			String errorMessage = "Release package not found.";
			supportRegister.handleSystemError(codeSystem, errorMessage, new ServiceException(errorMessage, e));
			throw new ServiceExceptionWithStatusCode(errorMessage, HttpStatus.NOT_FOUND);
		} catch (IOException e) {
			String errorMessage = "Failed to download release package.";
			supportRegister.handleSystemError(codeSystem, errorMessage, new ServiceException(errorMessage, e));
			throw new ServiceExceptionWithStatusCode(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private SRSBuild getReleaseCompleteBuildOrThrow(CodeSystem codeSystem) throws ServiceException {
		if (codeSystem.getEditionStatus() != EditionStatus.RELEASE || codeSystem.getBuildStatus() != CodeSystemBuildStatus.COMPLETE) {
			throw new ServiceExceptionWithStatusCode("This function is only available when CodeSytem is in release mode " +
					"and the release candidate build is complete.", HttpStatus.CONFLICT);
		}

		String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
		SRSBuild build = releaseServiceClient.getBuild(buildUrl);
		String buildStatus = build.status();
		if (CodeSystemBuildStatus.fromSRSStatus(buildStatus) != CodeSystemBuildStatus.COMPLETE) {
			throw new ServiceExceptionWithStatusCode("The release candidate build is not yet complete.", HttpStatus.CONFLICT);
		}
		return build;
	}
}
