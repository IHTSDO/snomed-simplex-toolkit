package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.domain.JobStatus;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
public class SupportRegister {

	private final Logger supportLog = LoggerFactory.getLogger(getClass());

	public void handleContentIssue(AsyncJob job, String errorMessage) {
		job.setStatus(JobStatus.CONTENT_ISSUE);
		job.setErrorMessage(format("%s. Please contact support.", errorMessage));
		supportLog.info("Support Issue|Content|CodeSystem:{}, Job:{}|{}, Message:{}", job.getCodeSystem(), job.getId(), job.getDisplay(), errorMessage);
	}

	public void handleSystemIssue(AsyncJob job, String errorMessage) {
		handleSystemIssue(job, errorMessage, null);
	}

	public void handleSystemIssue(AsyncJob job, String errorMessage, ServiceException exception) {
		job.setStatus(JobStatus.ERROR);
		job.setErrorMessage(errorMessage);
		if (exception != null) {
			job.setServiceException(exception);
		}
		job.setErrorMessage(format("%s The support team have been made aware. Please try again later.", errorMessage));
		supportLog.info("Support Issue|System|CodeSystem:{}, Job:{}|{}, Message:{}", job.getCodeSystem(), job.getId(), job.getDisplay(), errorMessage, exception);
	}
}
