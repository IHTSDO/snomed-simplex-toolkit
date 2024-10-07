package org.snomed.simplex.exceptions;

import org.snomed.simplex.domain.JobStatus;
import org.springframework.http.HttpStatus;

public class ServiceExceptionWithStatusCode extends ServiceException {

	private final int statusCode;
	private final JobStatus jobStatus;

	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode, JobStatus jobStatus) {
		super(message);
		this.statusCode = statusCode.value();
		this.jobStatus = jobStatus;
	}

	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode) {
		this(message, statusCode, null);
	}

	public int getStatusCode() {
		return statusCode;
	}

	public JobStatus getJobStatus() {
		return jobStatus;
	}

}
