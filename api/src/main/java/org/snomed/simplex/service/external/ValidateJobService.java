package org.snomed.simplex.service.external;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemValidationStatus;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;

@Service
public class ValidateJobService extends ExternalFunctionJobService<Void> {

	private final SnowstormClientFactory snowstormClientFactory;
	private final ValidationServiceClient validationServiceClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValidateJobService(
			SupportRegister supportRegister,
			ActivityService activityService,
			SnowstormClientFactory snowstormClientFactory,
			ValidationServiceClient validationServiceClient) {

		super(supportRegister, activityService);
		this.snowstormClientFactory = snowstormClientFactory;
		this.validationServiceClient = validationServiceClient;
	}

	@Override
	protected String getFunctionName() {
		return "validation";
	}

	@Override
	protected String doCallService(CodeSystem codeSystem, ExternalServiceJob asyncJob, Void requestParam) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		URI validationUri = validationServiceClient.startValidation(codeSystem, snowstormClient);
		logger.info("Created validation. Branch:{}, RVF Job:{}", codeSystem.getWorkingBranchPath(), validationUri);
		asyncJob.setLink(validationUri.toString());
		snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(), Map.of(Branch.LATEST_VALIDATION_REPORT_METADATA_KEY, validationUri.toString()));
		return validationUri.toString();
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob job, String validationUrl) {
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
		return validationComplete;
	}

	public void addValidationStatus(CodeSystem codeSystem) throws ServiceException {
		CodeSystemValidationStatus status = CodeSystemValidationStatus.TODO;
		ExternalServiceJob latestJob = getLatestJob(codeSystem.getShortName());
		if (latestJob != null) {
			status = getStatusFromCurrentJob(codeSystem, latestJob, status);
		} else {
			// Service may have been restarted. Attempt recovery of status using existing report.
			if (codeSystem.getLatestValidationReport() != null) {
				status = getStatusFromRVFReport(codeSystem, status);
			}
		}
		codeSystem.setValidationStatus(status);
	}

	private @NotNull CodeSystemValidationStatus getStatusFromCurrentJob(CodeSystem codeSystem, ExternalServiceJob latestJob, CodeSystemValidationStatus status) {
		switch (latestJob.getStatus()) {
			case QUEUED, IN_PROGRESS -> status = CodeSystemValidationStatus.IN_PROGRESS;
			case SYSTEM_ERROR -> status = CodeSystemValidationStatus.SYSTEM_ERROR;
			case TECHNICAL_CONTENT_ISSUE ->
				// Not expected
					status = CodeSystemValidationStatus.SYSTEM_ERROR;
			case USER_CONTENT_ERROR -> status = CodeSystemValidationStatus.CONTENT_ERROR;
			case USER_CONTENT_WARNING -> status = CodeSystemValidationStatus.CONTENT_WARNING;
			case COMPLETE -> status = CodeSystemValidationStatus.COMPLETE;
		}
		if (status.isCanTurnStale() && latestJob.getContentHeadTimestamp() != codeSystem.getContentHeadTimestamp()) {
			logger.info("Validation report {} was {} is now stale.", latestJob.getLink(), status);
			logger.debug("Validation report {} was {} is now stale, validationHead:{}, contentHead:{}.",
					latestJob.getLink(), status, latestJob.getContentHeadTimestamp(), codeSystem.getContentHeadTimestamp());

			status = CodeSystemValidationStatus.STALE;
		}
		return status;
	}

	private CodeSystemValidationStatus getStatusFromRVFReport(CodeSystem codeSystem, CodeSystemValidationStatus status) throws ServiceException {
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
		return status;
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

}
