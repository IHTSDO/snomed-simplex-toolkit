package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.util.ExceptionUtil;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
public class SupportRegister {

	private final Logger supportLog = LoggerFactory.getLogger(getClass());

	public void handleTechnicalContentIssue(AsyncJob job, String errorMessage) {
		job.setStatus(JobStatus.TECHNICAL_CONTENT_ISSUE);
		job.setErrorMessage(format("%s. Please contact support.", errorMessage));
		supportLog.error("Support Issue|Content|CodeSystem:{}| Job:{},{}| MESSAGE:{}", job.getCodeSystem(), job.getId(), job.getDisplay(), errorMessage);
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
		String stackTrace = getStackTrace(exception);
		supportLog.error("Support Issue|System|CodeSystem:{}| Job:{},{}| MESSAGE:{}| STACK_TRACE:{}", job.getCodeSystem(), job.getId(),
				job.getDisplay(), errorMessage, stackTrace);
		supportLog.info("Stack trace", exception);
	}

	public void handleSystemError(CodeSystem codeSystem, String errorMessage, ServiceException exception) {
		String stackTrace = getStackTrace(exception);
		supportLog.error("Support Issue|System|CodeSystem:{}| MESSAGE:{}| STACK_TRACE:{}", codeSystem.getShortName(), errorMessage, stackTrace);
		supportLog.info("Stack trace", exception);
	}

	private String getStackTrace(ServiceException exception) {
		return exception != null ? ExceptionUtil.getStackTraceAsString(exception).replace("\n", "") : "none";
	}
}
