package org.snomed.simplex.service.external;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.EditionStatus;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.stereotype.Service;

import static org.snomed.simplex.service.CodeSystemService.*;

@Service
public class PublishReleaseJobService extends ExternalFunctionJobService<Void> {

	private final SnowstormClientFactory snowstormClientFactory;
	private final ReleaseServiceClient releaseServiceClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public PublishReleaseJobService(
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
		return "publish release";
	}

	@Override
	protected String doCallService(CodeSystem codeSystem, ExternalServiceJob job, Void requestParam) throws ServiceException {
		publishingStatusCheck(codeSystem);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		SRSBuild build = releaseServiceClient.getReleaseCompleteBuildOrThrow(codeSystem);
		String effectiveTime = build.configuration().getEffectiveTime();

		setEditionStatus(codeSystem, EditionStatus.PUBLISHING, snowstormClient);

		logger.info("Versioning CodeSystem {}", codeSystem.getShortName());
		snowstormClient.versionCodeSystem(codeSystem, effectiveTime);

		logger.info("Publishing Release {}", build);
		releaseServiceClient.publishBuild(build);
		return null;
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob value, String externalId) {
		// Publishing takes 10 hours so taking a different approach to monitoring.
		return false;
	}

	public @NotNull CodeSystem handleCodeSystemPublishing(String codeSystemShortName, CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
		SRSBuild build = releaseServiceClient.getBuild(buildUrl);
		if (build.getTags().contains("PUBLISHED")) {
			synchronized (this) {
				// After acquiring the lock, is status still publishing?
				codeSystem = snowstormClient.getCodeSystemForDisplay(codeSystemShortName);
				if (codeSystem.getEditionStatus() == EditionStatus.PUBLISHING) {
					String effectiveTime = build.configuration().getEffectiveTime();
					String releasePackageFilepath = "%s/%s".formatted(effectiveTime, releaseServiceClient.getReleasePackageFilename(buildUrl));
					snowstormClient.setVersionReleasePackage(codeSystem, effectiveTime, releasePackageFilepath);

					Activity finaliseActivity = activityService.findLatestByCodeSystemAndActivityType(codeSystemShortName, ActivityType.FINALIZE_RELEASE);
					if (finaliseActivity != null) {
						activityService.endAsynchronousActivity(finaliseActivity);
					}

					setEditionStatus(codeSystem, EditionStatus.AUTHORING, snowstormClient);
					clearBuildStatus(codeSystem, snowstormClient);
					Activity activity = new Activity(Activity.AUTOMATIC, codeSystemShortName, ComponentType.CODE_SYSTEM, ActivityType.START_AUTHORING);
					activityService.endAsynchronousActivity(activity);
				}
			}
		}
		return codeSystem;
	}
}
