package org.snomed.simplex.service.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.EditionStatus;
import org.snomed.simplex.client.domain.SnowstormUpgradeJob;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CodeSystemUpgradeRequest;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URI;

import static org.snomed.simplex.service.CodeSystemService.publishingStatusCheck;
import static org.snomed.simplex.service.CodeSystemService.setEditionStatus;

@Service
public class UpgradeJobService extends ExternalFunctionJobService<CodeSystemUpgradeRequest> {

	private final SnowstormClientFactory snowstormClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public UpgradeJobService(
			SupportRegister supportRegister,
			ActivityService activityService,
			SnowstormClientFactory snowstormClientFactory) {

		super(supportRegister, activityService);
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@Override
	protected String getFunctionName() {
		return "Code System Upgrade";
	}

	@Override
	protected String doCallService(CodeSystem codeSystem, ExternalServiceJob job, CodeSystemUpgradeRequest upgradeRequest) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		publishingStatusCheck(codeSystem);

		// Upgrade version check
		if (upgradeRequest.newDependantVersion() <= codeSystem.getDependantVersionEffectiveTime()) {
			throw new ServiceExceptionWithStatusCode("The upgrade version much be newer than the current version.", HttpStatus.CONFLICT,
					JobStatus.USER_CONTENT_ERROR);
		}

		setEditionStatus(codeSystem, EditionStatus.MAINTENANCE, snowstormClient);
		// Disable daily-build to prevent content rollback during upgrade
		codeSystem.setDailyBuildAvailable(false);
		snowstormClient.updateCodeSystem(codeSystem);
		URI upgradeJobLocation = snowstormClient.createUpgradeJob(codeSystem, upgradeRequest);
		logger.info("Created upgrade job. Codesystem:{}, Snowstorm Job:{}", codeSystem.getShortName(), upgradeJobLocation);
		job.setLink(upgradeJobLocation.toString());
		return upgradeJobLocation.toString();
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob job, String upgradeLocation) {
		SecurityContextHolder.setContext(job.getSecurityContext());
		boolean upgradeComplete = false;
		try {
			// Get client using the security context of the user who created the job
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			SnowstormUpgradeJob upgradeJob = snowstormClient.getUpgradeJob(upgradeLocation);
			SnowstormUpgradeJob.Status status = upgradeJob.getStatus();
			CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(job.getCodeSystem());
			if (status == SnowstormUpgradeJob.Status.COMPLETED) {
				codeSystem.setDailyBuildAvailable(true);
				snowstormClient.updateCodeSystem(codeSystem);
				setEditionStatus(codeSystem, EditionStatus.AUTHORING, snowstormClient);
				logger.info("Upgrade complete. Codesystem:{}", codeSystem.getShortName());
				job.setStatus(JobStatus.COMPLETE);
				upgradeComplete = true;
			} else if (status == SnowstormUpgradeJob.Status.FAILED) {
				supportRegister.handleSystemError(job, "Upgrade failed to run in Terminology Server.");
				setEditionStatus(codeSystem, EditionStatus.AUTHORING, snowstormClient);
				upgradeComplete = true;
			}
		} catch (ServiceException e) {
			supportRegister.handleSystemError(job, "Terminology Server API issue.", e);
			upgradeComplete = true;
		}
		return upgradeComplete;
	}
}
