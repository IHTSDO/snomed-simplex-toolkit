package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.job.AsyncJob;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
public class SupportRegister {

	private final Logger supportLog = LoggerFactory.getLogger(getClass());

	public void handleTechnicalContentIssue(AsyncJob job, String errorMessage) {
		job.setStatus(JobStatus.TECHNICAL_CONTENT_ISSUE);
		job.setErrorMessage(format("%s. Please contact support.", errorMessage));
		supportLog.info("Support Issue|Content|CodeSystem:{}, Job:{}|{}, Message:{}", job.getCodeSystem(), job.getId(), job.getDisplay(), errorMessage);
	}

	public void handleSystemError(AsyncJob job, String errorMessage) throws ServiceException {
		if (job == null) {
			throw new ServiceException(errorMessage);
		}
		handleSystemError(job, errorMessage, null);
	}

	public void handleSystemError(AsyncJob job, String errorMessage, ServiceException exception) {
		job.setStatus(JobStatus.SYSTEM_ERROR);
		job.setErrorMessage(errorMessage);
		if (exception != null) {
			job.setServiceException(exception);
		}
		job.setErrorMessage(format("%s The support team have been made aware. Please try again later.", errorMessage));
		supportLog.info("Support Issue|System|CodeSystem:{}, Job:{}|{}, Message:{}", job.getCodeSystem(), job.getId(), job.getDisplay(), errorMessage, exception);
	}
}
