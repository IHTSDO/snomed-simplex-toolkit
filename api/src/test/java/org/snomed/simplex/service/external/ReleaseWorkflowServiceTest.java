package org.snomed.simplex.service.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@ExtendWith(MockitoExtension.class)
class ReleaseWorkflowServiceTest {

	private static final String CODE_SYSTEM_SHORT_NAME = "SNOMEDCT-TEST";
	private static final String EFFECTIVE_TIME = "20260731";

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private SnowstormClient snowstormClient;

	@Mock
	private CodeSystemService codeSystemService;

	@Mock
	private ReleaseServiceClient releaseServiceClient;

	@Mock
	private ReleaseCandidateJobService releaseCandidateJobService;

	@Mock
	private PublishReleaseJobService publishReleaseJobService;

	@Mock
	private ActivityService activityService;

	private ReleaseWorkflowService releaseWorkflowService;

	@BeforeEach
	void setUp() throws Exception {
		releaseWorkflowService = new ReleaseWorkflowService(
				snowstormClientFactory, codeSystemService, releaseServiceClient,
				releaseCandidateJobService, publishReleaseJobService, activityService);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
	}

	@Test
	void startReleaseCandidate_buildStatusInProgress_throwsConflict() throws ServiceException {
		CodeSystem codeSystem = releaseReadyCodeSystem();
		codeSystem.setBuildStatus(CodeSystemBuildStatus.IN_PROGRESS);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> releaseWorkflowService.startReleaseCandidate(CODE_SYSTEM_SHORT_NAME, EFFECTIVE_TIME));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
		verify(releaseServiceClient, never()).getCreateProduct(any(), any());
		verify(activityService, never()).startExternalServiceActivity(any(), any(), any(), any(), any());
	}

	@Test
	void startReleaseCandidate_openBuildReleaseActivity_throwsConflict() throws ServiceException {
		CodeSystem codeSystem = releaseReadyCodeSystem();
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		Activity openActivity = new Activity("user", CODE_SYSTEM_SHORT_NAME, CODE_SYSTEM, ActivityType.BUILD_RELEASE);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.BUILD_RELEASE))
				.thenReturn(openActivity);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> releaseWorkflowService.startReleaseCandidate(CODE_SYSTEM_SHORT_NAME, EFFECTIVE_TIME));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
		verify(releaseServiceClient, never()).getCreateProduct(any(), any());
	}

	@Test
	void startReleaseCandidate_happyPath_startsBuild() throws ServiceException {
		CodeSystem codeSystem = releaseReadyCodeSystem();
		PackageConfiguration packageConfiguration = new PackageConfiguration("Org", "contact@example.com");
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		when(codeSystemService.getPackageConfiguration(any())).thenReturn(packageConfiguration);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.BUILD_RELEASE))
				.thenReturn(null);
		when(releaseCandidateJobService.getLatestJob(CODE_SYSTEM_SHORT_NAME)).thenReturn(null);

		releaseWorkflowService.startReleaseCandidate(CODE_SYSTEM_SHORT_NAME, EFFECTIVE_TIME);

		verify(snowstormClient).invalidateCodeSystemCache(CODE_SYSTEM_SHORT_NAME);
		verify(snowstormClient).upsertBranchMetadata(eq(codeSystem.getBranchPath()), eq(Map.of(
				Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.IN_PROGRESS.name())));
		verify(releaseServiceClient).getCreateProduct(codeSystem, packageConfiguration);
		verify(activityService).startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM), eq(ActivityType.BUILD_RELEASE),
				eq(releaseCandidateJobService), eq(EFFECTIVE_TIME));
	}

	@Test
	void startReleaseCandidate_getCreateProductFailure_clearsBuildStatus() throws ServiceException {
		CodeSystem codeSystem = releaseReadyCodeSystem();
		PackageConfiguration packageConfiguration = new PackageConfiguration("Org", "contact@example.com");
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		when(codeSystemService.getPackageConfiguration(any())).thenReturn(packageConfiguration);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.BUILD_RELEASE))
				.thenReturn(null);
		when(releaseCandidateJobService.getLatestJob(CODE_SYSTEM_SHORT_NAME)).thenReturn(null);
		when(releaseServiceClient.getCreateProduct(codeSystem, packageConfiguration))
				.thenThrow(new ServiceException("SRS failure"));

		assertThrows(ServiceException.class,
				() -> releaseWorkflowService.startReleaseCandidate(CODE_SYSTEM_SHORT_NAME, EFFECTIVE_TIME));

		verify(snowstormClient).upsertBranchMetadata(eq(codeSystem.getBranchPath()), eq(Map.of(
				Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.TODO.name())));
		verify(activityService, never()).startExternalServiceActivity(any(), any(), any(), any(), any());
	}

	@Test
	void finalizeRelease_editionStatusPublishing_throwsConflict() throws ServiceException {
		CodeSystem codeSystem = codeSystem();
		codeSystem.setEditionStatus(EditionStatus.PUBLISHING);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> releaseWorkflowService.finalizeRelease(CODE_SYSTEM_SHORT_NAME));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
		verify(activityService, never()).startExternalServiceActivity(any(), any(), any(), any(), isNull());
	}

	@Test
	void finalizeRelease_openFinalizeReleaseActivity_throwsConflict() throws ServiceException {
		CodeSystem codeSystem = codeSystem();
		codeSystem.setEditionStatus(EditionStatus.RELEASE);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		Activity openActivity = new Activity("user", CODE_SYSTEM_SHORT_NAME, CODE_SYSTEM, ActivityType.FINALIZE_RELEASE);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.FINALIZE_RELEASE))
				.thenReturn(openActivity);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> releaseWorkflowService.finalizeRelease(CODE_SYSTEM_SHORT_NAME));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
	}

	@Test
	void finalizeRelease_inMemoryPublishJobInProgress_throwsConflict() throws ServiceException {
		CodeSystem codeSystem = codeSystem();
		codeSystem.setEditionStatus(EditionStatus.RELEASE);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.FINALIZE_RELEASE))
				.thenReturn(null);
		ExternalServiceJob publishJob = new ExternalServiceJob(codeSystem, "Publish");
		publishJob.setStatus(JobStatus.IN_PROGRESS);
		when(publishReleaseJobService.getLatestJob(CODE_SYSTEM_SHORT_NAME)).thenReturn(publishJob);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> releaseWorkflowService.finalizeRelease(CODE_SYSTEM_SHORT_NAME));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
	}

	@Test
	void finalizeRelease_happyPath_startsPublishing() throws ServiceException {
		CodeSystem codeSystem = codeSystem();
		codeSystem.setEditionStatus(EditionStatus.RELEASE);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.FINALIZE_RELEASE))
				.thenReturn(null);
		when(publishReleaseJobService.getLatestJob(CODE_SYSTEM_SHORT_NAME)).thenReturn(null);

		releaseWorkflowService.finalizeRelease(CODE_SYSTEM_SHORT_NAME);

		verify(snowstormClient).invalidateCodeSystemCache(CODE_SYSTEM_SHORT_NAME);
		verify(snowstormClient).upsertBranchMetadata(eq(codeSystem.getBranchPath()), eq(Map.of(
				Branch.EDITION_STATUS_METADATA_KEY, EditionStatus.PUBLISHING.name())));
		verify(activityService).startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM), eq(ActivityType.FINALIZE_RELEASE),
				eq(publishReleaseJobService), isNull());
	}

	@Test
	void finalizeRelease_activityStartFailure_rollsBackEditionStatus() throws ServiceException {
		CodeSystem codeSystem = codeSystem();
		codeSystem.setEditionStatus(EditionStatus.RELEASE);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.FINALIZE_RELEASE))
				.thenReturn(null);
		when(publishReleaseJobService.getLatestJob(CODE_SYSTEM_SHORT_NAME)).thenReturn(null);
		when(activityService.startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM), eq(ActivityType.FINALIZE_RELEASE),
				eq(publishReleaseJobService), isNull())).thenThrow(new ServiceException("Activity failed"));

		assertThrows(ServiceException.class, () -> releaseWorkflowService.finalizeRelease(CODE_SYSTEM_SHORT_NAME));

		verify(snowstormClient).upsertBranchMetadata(eq(codeSystem.getBranchPath()), eq(Map.of(
				Branch.EDITION_STATUS_METADATA_KEY, EditionStatus.RELEASE.name())));
	}

	private static CodeSystem codeSystem() {
		return new CodeSystem("Test", CODE_SYSTEM_SHORT_NAME, "MAIN/SNOMEDCT-TEST");
	}

	private static CodeSystem releaseReadyCodeSystem() {
		CodeSystem codeSystem = codeSystem();
		codeSystem.setEditionStatus(EditionStatus.RELEASE);
		codeSystem.setBuildStatus(CodeSystemBuildStatus.TODO);
		return codeSystem;
	}

}
