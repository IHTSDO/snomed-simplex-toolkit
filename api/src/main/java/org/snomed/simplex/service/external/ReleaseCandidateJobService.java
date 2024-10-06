package org.snomed.simplex.service.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemBuildStatus;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.snomed.simplex.service.CodeSystemService.setCodeSystemMetadata;

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
		CodeSystem.CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
		if (latestVersion != null && Integer.parseInt(effectiveTime) <= latestVersion.effectiveDate()) {
			job.setStatus(JobStatus.USER_CONTENT_ERROR);
			job.setErrorMessage(("The latest version of this Code System is %s. " +
					"The effective-time date of the new release candidate must be after the latest version.").formatted(latestVersion.effectiveDate()));
			return null;
		}

		SRSBuild releaseBuild = releaseServiceClient.buildProduct(codeSystem, effectiveTime);
		String releaseBuildUrl = releaseBuild.url();
		job.setLink(releaseBuildUrl);
		snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(),
				Map.of(Branch.LATEST_BUILD_METADATA_KEY, releaseBuildUrl,
						Branch.BUILD_STATUS_METADATA_KEY, CodeSystemBuildStatus.IN_PROGRESS.name()));

		return releaseBuildUrl;
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob job, String buildUrl) {
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
		if (buildComplete && buildStatus != null) {
			try {
				String codeSystem = job.getCodeSystem();
				SnowstormClient snowstormClient = snowstormClientFactory.getClient();
				CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
				setCodeSystemMetadata(Branch.BUILD_STATUS_METADATA_KEY, buildStatus.name(), theCodeSystem, snowstormClient);
			} catch (ServiceException e) {
				supportRegister.handleSystemError(job, "Failed to update build status in branch metadata", e);
			}
		}
		return buildComplete;
	}

}
