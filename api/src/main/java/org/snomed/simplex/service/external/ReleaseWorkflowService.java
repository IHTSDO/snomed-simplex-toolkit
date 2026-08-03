package org.snomed.simplex.service.external;

import org.apache.logging.log4j.util.Strings;
import org.jspecify.annotations.NonNull;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemBuildStatus;
import org.snomed.simplex.client.domain.EditionStatus;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.PackageConfiguration;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;
import static org.snomed.simplex.service.CodeSystemService.*;

@Service
public class ReleaseWorkflowService {

	private final SnowstormClientFactory snowstormClientFactory;
	private final CodeSystemService codeSystemService;
	private final ReleaseServiceClient releaseServiceClient;
	private final ReleaseCandidateJobService releaseCandidateJobService;
	private final PublishReleaseJobService publishReleaseJobService;
	private final ActivityService activityService;

	public ReleaseWorkflowService(
			SnowstormClientFactory snowstormClientFactory,
			CodeSystemService codeSystemService,
			ReleaseServiceClient releaseServiceClient,
			ReleaseCandidateJobService releaseCandidateJobService,
			PublishReleaseJobService publishReleaseJobService,
			ActivityService activityService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.codeSystemService = codeSystemService;
		this.releaseServiceClient = releaseServiceClient;
		this.releaseCandidateJobService = releaseCandidateJobService;
		this.publishReleaseJobService = publishReleaseJobService;
		this.activityService = activityService;
	}

	public void startReleaseCandidate(String codeSystemShortName, String effectiveTime) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.invalidateCodeSystemCache(codeSystemShortName);
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(codeSystemShortName);

		if (codeSystem.getEditionStatus() != EditionStatus.RELEASE) {
			throw new ServiceExceptionWithStatusCode("CodeSystem must be approved for release before creating a release candidate.", HttpStatus.CONFLICT);
		}

		if (codeSystem.getBuildStatus() == CodeSystemBuildStatus.IN_PROGRESS) {
			throw createReleaseAlreadyInProgressError();
		}
		Activity latestBuildActivity = activityService.findLatestByCodeSystemAndActivityType(codeSystemShortName, ActivityType.BUILD_RELEASE);
		if (latestBuildActivity != null && latestBuildActivity.getEndDate() == null) {
			throw createReleaseAlreadyInProgressError();
		}
		ExternalServiceJob inMemoryBuildJob = releaseCandidateJobService.getLatestJob(codeSystemShortName);
		if (inMemoryBuildJob != null && inMemoryBuildJob.getStatus() == JobStatus.IN_PROGRESS) {
			throw createReleaseAlreadyInProgressError();
		}

		PackageConfiguration packageConfiguration = codeSystemService.getPackageConfiguration(codeSystem.getBranchObject());
		if (Strings.isBlank(packageConfiguration.orgName()) || Strings.isBlank(packageConfiguration.orgContactDetails())) {
			throw new ServiceExceptionWithStatusCode("Organisation name and contact details must be set before creating a build.", HttpStatus.CONFLICT);
		}

		setCodeSystemMetadata(Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.IN_PROGRESS.name(), codeSystem, snowstormClient);

		try {
			releaseServiceClient.getCreateProduct(codeSystem, packageConfiguration);
			activityService.startExternalServiceActivity(codeSystem, CODE_SYSTEM, ActivityType.BUILD_RELEASE, releaseCandidateJobService, effectiveTime);
		} catch (ServiceException e) {
			clearBuildStatus(codeSystem, snowstormClient);
			throw e;
		}
	}

	private static @NonNull ServiceExceptionWithStatusCode createReleaseAlreadyInProgressError() {
		return new ServiceExceptionWithStatusCode("A release candidate build is already in progress.", HttpStatus.CONFLICT);
	}

	public void finalizeRelease(String codeSystemShortName) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.invalidateCodeSystemCache(codeSystemShortName);
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(codeSystemShortName);

		if (codeSystem.getEditionStatus() == EditionStatus.PUBLISHING) {
			throw createPublishingInProgressError();
		}
		Activity latestFinalizeActivity = activityService.findLatestByCodeSystemAndActivityType(codeSystemShortName, ActivityType.FINALIZE_RELEASE);
		if (latestFinalizeActivity != null && latestFinalizeActivity.getEndDate() == null) {
			throw createPublishingInProgressError();
		}
		ExternalServiceJob inMemoryPublishJob = publishReleaseJobService.getLatestJob(codeSystemShortName);
		if (inMemoryPublishJob != null && inMemoryPublishJob.getStatus() == JobStatus.IN_PROGRESS) {
			throw createPublishingInProgressError();
		}

		EditionStatus previousStatus = codeSystem.getEditionStatus();
		setEditionStatus(codeSystem, EditionStatus.PUBLISHING, snowstormClient);

		try {
			activityService.startExternalServiceActivity(codeSystem, CODE_SYSTEM, ActivityType.FINALIZE_RELEASE, publishReleaseJobService, null);
		} catch (ServiceException e) {
			setEditionStatus(codeSystem, previousStatus, snowstormClient);
			throw e;
		}
	}

	private static @NonNull ServiceExceptionWithStatusCode createPublishingInProgressError() {
		return new ServiceExceptionWithStatusCode("Publishing is already in progress.", HttpStatus.CONFLICT);
	}

}
