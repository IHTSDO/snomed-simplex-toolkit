package org.snomed.simplex.service.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemClassificationStatus;
import org.snomed.simplex.client.domain.SnowstormClassificationJob;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class ClassifyJobService extends ExternalFunctionJobService<Void> {

	private final SnowstormClientFactory snowstormClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ClassifyJobService(
			SupportRegister supportRegister,
			ActivityService activityService,
			SnowstormClientFactory snowstormClientFactory) {

		super(supportRegister, activityService);
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@Override
	protected String getFunctionName() {
		return "classification";
	}

	@Override
	protected String doCallService(CodeSystem codeSystem, ExternalServiceJob asyncJob, Void requestParam) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		String branch = asyncJob.getBranch();
		String classificationId = snowstormClient.createClassification(branch);
		logger.info("Started classification. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, asyncJob.getId());
		asyncJob.setLink(classificationId);
		return classificationId;
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob job, String classificationId) {
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
		return classificationComplete;
	}

	public void addClassificationStatus(CodeSystem theCodeSystem) {
		CodeSystemClassificationStatus status;
		if (theCodeSystem.isClassified()) {
			status = CodeSystemClassificationStatus.COMPLETE;
		} else {
			status = CodeSystemClassificationStatus.TODO;

			AsyncJob latestJobOfType = getLatestJob(theCodeSystem.getShortName());
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
}
