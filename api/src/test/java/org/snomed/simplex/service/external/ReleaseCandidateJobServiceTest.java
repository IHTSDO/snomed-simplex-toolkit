package org.snomed.simplex.service.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemBuildStatus;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.client.srs.domain.SRSBuildConfiguration;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@ExtendWith(MockitoExtension.class)
class ReleaseCandidateJobServiceTest {

	private static final String CODE_SYSTEM_SHORT_NAME = "SNOMEDCT-TEST";
	private static final String BUILD_URL = "https://release.example/builds/2026-05-26T17:56:54";

	@Mock
	private SupportRegister supportRegister;

	@Mock
	private ActivityService activityService;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private SnowstormClient snowstormClient;

	@Mock
	private ReleaseServiceClient releaseServiceClient;

	private ReleaseCandidateJobService releaseCandidateJobService;

	@BeforeEach
	void setUp() {
		releaseCandidateJobService = new ReleaseCandidateJobService(
				supportRegister, activityService, snowstormClientFactory, releaseServiceClient);
	}

	@Test
	void doMonitorProgress_failedPreConditions_stopsJobAndClearsBuildStatus() throws ServiceException {
		ExternalServiceJob job = createJob();
		SRSBuild build = new SRSBuild("2026-05-26T17:56:54", BUILD_URL, "2026-05-26T17:56:54", "FAILED_PRE_CONDITIONS",
				Collections.emptyList(), new SRSBuildConfiguration("20260731"));
		CodeSystem codeSystem = codeSystemWithBranch();

		when(releaseServiceClient.getBuild(BUILD_URL)).thenReturn(build);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);

		boolean complete = releaseCandidateJobService.doMonitorProgress(job, BUILD_URL);

		assertTrue(complete);
		verify(supportRegister).handleSystemError(job, "SRS build failed with status FAILED_PRE_CONDITIONS.");
		verify(snowstormClient).upsertBranchMetadata("MAIN/SNOMEDCT-TEST", Map.of(Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.TODO.name()));
	}

	@Test
	void doMonitorProgress_building_keepsPolling() throws ServiceException {
		ExternalServiceJob job = createJob();
		SRSBuild build = new SRSBuild("2026-05-26T17:56:54", BUILD_URL, "2026-05-26T17:56:54", "BUILDING",
				Collections.emptyList(), new SRSBuildConfiguration("20260731"));

		when(releaseServiceClient.getBuild(BUILD_URL)).thenReturn(build);

		boolean complete = releaseCandidateJobService.doMonitorProgress(job, BUILD_URL);

		assertFalse(complete);
		assertEquals(JobStatus.IN_PROGRESS, job.getStatus());
		verify(supportRegister, never()).handleSystemError(any(), any());
		verify(snowstormClientFactory, never()).getClient();
	}

	@Test
	void doMonitorProgress_releaseComplete_updatesMetadata() throws ServiceException {
		ExternalServiceJob job = createJob();
		SRSBuild build = new SRSBuild("2026-05-26T17:56:54", BUILD_URL, "2026-05-26T17:56:54", "RELEASE_COMPLETE",
				Collections.emptyList(), new SRSBuildConfiguration("20260731"));
		CodeSystem codeSystem = codeSystemWithBranch();

		when(releaseServiceClient.getBuild(BUILD_URL)).thenReturn(build);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM_SHORT_NAME)).thenReturn(codeSystem);

		boolean complete = releaseCandidateJobService.doMonitorProgress(job, BUILD_URL);

		assertTrue(complete);
		assertEquals(JobStatus.COMPLETE, job.getStatus());
		verify(snowstormClient).upsertBranchMetadata("MAIN/SNOMEDCT-TEST",
				Map.of(Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.COMPLETE.name()));
	}

	@Test
	void recoverOrphanedBuild_terminalFailure_clearsStatusAndEndsActivity() throws ServiceException {
		CodeSystem codeSystem = codeSystemWithBranch();
		codeSystem.setBuildStatus(CodeSystemBuildStatus.IN_PROGRESS);
		codeSystem.setLatestReleaseCandidateBuild(BUILD_URL);

		Activity activity = new Activity("user", CODE_SYSTEM_SHORT_NAME, CODE_SYSTEM, ActivityType.BUILD_RELEASE);
		SRSBuild build = new SRSBuild("2026-05-26T17:56:54", BUILD_URL, "2026-05-26T17:56:54", "FAILED_PRE_CONDITIONS",
				Collections.emptyList(), new SRSBuildConfiguration("20260731"));

		when(releaseServiceClient.getBuild(BUILD_URL)).thenReturn(build);
		when(activityService.findLatestByCodeSystemAndActivityType(CODE_SYSTEM_SHORT_NAME, ActivityType.BUILD_RELEASE))
				.thenReturn(activity);

		releaseCandidateJobService.recoverOrphanedBuild(codeSystem, snowstormClient);

		verify(snowstormClient).upsertBranchMetadata("MAIN/SNOMEDCT-TEST",
				Map.of(Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.TODO.name()));
		ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
		verify(activityService).endAsynchronousActivity(activityCaptor.capture());
		assertTrue(activityCaptor.getValue().isError());
	}

	private ExternalServiceJob createJob() {
		CodeSystem codeSystem = codeSystemWithBranch();
		ExternalServiceJob job = new ExternalServiceJob(codeSystem, "Release Candidate Build", "MAIN/SNOMEDCT-TEST", 1L);
		Activity activity = new Activity("user", CODE_SYSTEM_SHORT_NAME, CODE_SYSTEM, ActivityType.BUILD_RELEASE);
		job.setActivity(activity);
		job.setSecurityContext(SecurityContextHolder.createEmptyContext());
		return job;
	}

	private CodeSystem codeSystemWithBranch() {
		return new CodeSystem("Test", CODE_SYSTEM_SHORT_NAME, "MAIN/SNOMEDCT-TEST");
	}
}
