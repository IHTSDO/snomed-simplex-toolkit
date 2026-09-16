package org.snomed.simplex.service.external;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemBuildStatus;
import org.snomed.simplex.client.domain.CodeSystemVersion;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import static org.snomed.simplex.service.CodeSystemService.clearBuildStatus;
import static org.snomed.simplex.service.CodeSystemService.setReleaseBuildInProgress;
import static org.snomed.simplex.service.CodeSystemService.setReleaseBuildStatus;

@Service
public class ReleaseCandidateJobService extends ExternalFunctionJobService<String> {

	private final SnowstormClientFactory snowstormClientFactory;
	private final ReleaseServiceClient releaseServiceClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ReleaseCandidateJobService(
			SupportRegister supportRegister,
			ActivityService activityService,
			SnowstormClientFactory snowstormClientFactory,
			ReleaseServiceClient releaseServiceClient) {

		super(supportRegister, activityService);
		this.snowstormClientFactory = snowstormClientFactory;
		this.releaseServiceClient = releaseServiceClient;
	}

	@Override
	protected String getFunctionName() {
		return "Release Candidate Build";
	}

	@Override
	protected String doCallService(CodeSystem codeSystem, ExternalServiceJob job, String effectiveTime) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
		if (latestVersion != null && Integer.parseInt(effectiveTime) <= latestVersion.effectiveDate()) {
			job.setStatus(JobStatus.USER_CONTENT_ERROR);
			job.setErrorMessage(("The latest version of this Code System is %s. " +
					"The effective-time date of the new release candidate must be after the latest version.").formatted(latestVersion.effectiveDate()));
			return null;
		}

		SRSBuild releaseBuild = releaseServiceClient.buildProduct(codeSystem, effectiveTime);
		String releaseBuildUrl = releaseBuild.url();
		job.setLink(releaseBuildUrl);
		setReleaseBuildInProgress(codeSystem, releaseBuildUrl, snowstormClient);

		return releaseBuildUrl;
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob job, String buildUrl) {
		SecurityContextHolder.setContext(job.getSecurityContext());
		try {
			SRSBuild build = releaseServiceClient.getBuild(buildUrl);
			if (build == null) {
				supportRegister.handleSystemError(job, "SRS build response was empty.");
				clearBuildStatusForJob(job);
				return true;
			}

			String rawStatus = build.status();
			CodeSystemBuildStatus buildStatus = CodeSystemBuildStatus.fromSRSStatus(rawStatus);
			logger.debug("SRS build status {} (mapped to {}) for {}", rawStatus, buildStatus, buildUrl);

			return switch (buildStatus) {
				case IN_PROGRESS -> {
					job.setStatus(JobStatus.IN_PROGRESS);
					yield false;
				}
				case FAILED -> {
					supportRegister.handleSystemError(job, "SRS build failed with status %s.".formatted(rawStatus));
					clearBuildStatusForJob(job);
					yield true;
				}
				case COMPLETE -> {
					logger.info("Build completed. Branch:{}, SRS Job:{}, Status:{}", job.getBranch(), buildUrl, rawStatus);
					job.setStatus(JobStatus.COMPLETE);
					yield updateBuildStatusMetadata(job, buildStatus);
				}
				case TODO -> {
					supportRegister.handleSystemError(job, "SRS build has no status.");
					clearBuildStatusForJob(job);
					yield true;
				}
			};
		} catch (ServiceException e) {
			supportRegister.handleSystemError(job, "SRS API issue.", e);
			clearBuildStatusForJob(job);
			return true;
		} catch (RuntimeException e) {
			supportRegister.handleSystemError(job, "Unexpected error while checking SRS build status.",
					new ServiceException("Unexpected error while checking SRS build status.", e));
			clearBuildStatusForJob(job);
			return true;
		}
	}

	public void recoverOrphanedBuild(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		if (codeSystem.getBuildStatus() != CodeSystemBuildStatus.IN_PROGRESS) {
			return;
		}
		String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
		if (Strings.isBlank(buildUrl)) {
			Activity openActivity = activityService.findLatestByCodeSystemAndActivityType(
					codeSystem.getShortName(), ActivityType.BUILD_RELEASE);
			if (openActivity != null && openActivity.getEndDate() == null) {
				return;
			}
			return;
		}
		if (isJobBeingMonitored(buildUrl)) {
			return;
		}

		SRSBuild build = releaseServiceClient.getBuild(buildUrl);
		if (build == null) {
			logger.warn("Orphaned release build has no SRS response. CodeSystem:{}", codeSystem.getShortName());
			clearBuildStatus(codeSystem, snowstormClient);
			endOpenBuildReleaseActivity(codeSystem.getShortName(), true);
			return;
		}

		String rawStatus = build.status();
		CodeSystemBuildStatus buildStatus = CodeSystemBuildStatus.fromSRSStatus(rawStatus);
		logger.info("Recovering orphaned release build for {}. SRS status {} (mapped to {})",
				codeSystem.getShortName(), rawStatus, buildStatus);

		if (!buildStatus.isTerminal()) {
			return;
		}

		if (buildStatus == CodeSystemBuildStatus.COMPLETE) {
			setReleaseBuildStatus(codeSystem, buildStatus, snowstormClient);
			endOpenBuildReleaseActivity(codeSystem.getShortName(), false);
		} else {
			clearBuildStatus(codeSystem, snowstormClient);
			endOpenBuildReleaseActivity(codeSystem.getShortName(), true);
		}
	}

	private void clearBuildStatusForJob(ExternalServiceJob job) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(job.getCodeSystem());
			clearBuildStatus(codeSystem, snowstormClient);
		} catch (ServiceException e) {
			supportRegister.handleSystemError(job, "Failed to clear build status in branch metadata", e);
		}
	}

	private boolean updateBuildStatusMetadata(ExternalServiceJob job, CodeSystemBuildStatus buildStatus) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(job.getCodeSystem());
			setReleaseBuildStatus(codeSystem, buildStatus, snowstormClient);
			return true;
		} catch (ServiceException e) {
			supportRegister.handleSystemError(job, "Failed to update build status in branch metadata", e);
			// Stop monitoring: SRS is already terminal; retrying the poll won't help. Orphan recovery fixes stale metadata.
			return true;
		}
	}

	private void endOpenBuildReleaseActivity(String codeSystemShortName, boolean error) {
		Activity activity = activityService.findLatestByCodeSystemAndActivityType(codeSystemShortName, ActivityType.BUILD_RELEASE);
		if (activity != null && activity.getEndDate() == null) {
			if (error) {
				activity.setError(true);
				activity.setMessage("Release candidate build failed.");
			}
			activityService.endAsynchronousActivity(activity);
		}
	}

}
