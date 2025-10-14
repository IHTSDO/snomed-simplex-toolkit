package org.snomed.simplex.service;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.config.VersionedPackagesResourceManagerConfiguration;
import org.snomed.simplex.domain.PackageConfiguration;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CreateCodeSystemRequest;
import org.snomed.simplex.service.external.ClassifyJobService;
import org.snomed.simplex.service.external.PublishReleaseJobService;
import org.snomed.simplex.service.external.ReleaseCandidateJobService;
import org.snomed.simplex.service.external.ValidateJobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
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
	private final SupportRegister supportRegister;
	private final ClassifyJobService classifyService;
	private final ValidateJobService validateService;
	private final ReleaseCandidateJobService releaseCandidateJobService;
	private final PublishReleaseJobService publishReleaseJobService;
	private final ReleaseServiceClient releaseServiceClient;
	private final SecurityService securityService;
	private final ResourceManager versionedPackagesResourceManager;

	@Value("${simplex.short-name.max-length:70}")
	private int maxShortNameLength;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystemService(SnowstormClientFactory snowstormClientFactory, SupportRegister supportRegister,
			ClassifyJobService classifyService, ValidateJobService validateService,
			ReleaseCandidateJobService releaseCandidateJobService, ReleaseServiceClient releaseServiceClient,
			PublishReleaseJobService publishReleaseJobService,
			VersionedPackagesResourceManagerConfiguration resourceManagerConfiguration, ResourceLoader resourceLoader,
			SecurityService securityService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.supportRegister = supportRegister;
		this.classifyService = classifyService;
		this.validateService = validateService;
		this.releaseServiceClient = releaseServiceClient;
		this.publishReleaseJobService = publishReleaseJobService;
		this.releaseCandidateJobService = releaseCandidateJobService;
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
//		classifyService.addClassificationStatus(codeSystem);
//		validateService.addValidationStatus(codeSystem);
		securityService.updateUserRolePermissionCache(Collections.singletonList(codeSystem));

		runStatusChecks(codeSystem, snowstormClient);

		// If publishing, check SRS publish status
		// We are not using jobs for this because it takes 10 hours
		if (codeSystem.getEditionStatus() == EditionStatus.PUBLISHING) {
			codeSystem = publishReleaseJobService.handleCodeSystemPublishing(codeSystemShortName, codeSystem, snowstormClient);
		}

		return codeSystem;
	}

	private void runStatusChecks(CodeSystem codeSystem, SnowstormClient snowstormClient) {
		if (codeSystem.getBuildStatus() == CodeSystemBuildStatus.IN_PROGRESS) {
			String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
			if (buildUrl == null || !releaseCandidateJobService.isJobBeingMonitored(buildUrl)) {
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

	public static void publishingStatusCheck(CodeSystem codeSystem) {
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

	public static void clearBuildStatus(CodeSystem codeSystem, SnowstormClient snowstormClient) {
		CodeSystemBuildStatus newStatus = CodeSystemBuildStatus.TODO;
		codeSystem.setBuildStatus(newStatus);
		setCodeSystemMetadata(Branch.BUILD_STATUS_METADATA_KEY, newStatus.name(), codeSystem, snowstormClient);
	}

	public static void setCodeSystemMetadata(String key, String value, CodeSystem codeSystem, SnowstormClient snowstormClient) {
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

	public static void setEditionStatus(CodeSystem codeSystem, EditionStatus editionStatus, SnowstormClient snowstormClient) {
		codeSystem.setEditionStatus(editionStatus);
		setCodeSystemMetadata(Branch.EDITION_STATUS_METADATA_KEY, codeSystem.getEditionStatus().name(), codeSystem, snowstormClient);
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
		SRSBuild buildUrl = releaseServiceClient.getReleaseCompleteBuildOrThrow(codeSystem);

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

	public void addClassificationStatus(CodeSystem codeSystem) {
		classifyService.addClassificationStatus(codeSystem);
	}

	public ClassifyJobService getClassifyJobService() {
		return classifyService;
	}

	public ReleaseServiceClient getReleaseServiceClient() {
		return releaseServiceClient;
	}

	public ReleaseCandidateJobService getReleaseCandidateJobService() {
		return releaseCandidateJobService;
	}

	public PublishReleaseJobService getPublishReleaseJobService() {
		return publishReleaseJobService;
	}
}
