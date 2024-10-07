package org.snomed.simplex.exceptions;

import org.snomed.simplex.domain.JobStatus;
import org.springframework.http.HttpStatus;

public class ServiceExceptionWithStatusCode extends ServiceException {

	private final int statusCode;
	private JobStatus jobStatus;

	public ServiceExceptionWithStatusCode(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode) {
		this(message, statusCode.value());
	}

	public int getStatusCode() {
		return statusCode;
	}

	public JobStatus getJobStatus() {
		return jobStatus;
	}

	public ServiceExceptionWithStatusCode setJobStatus(JobStatus jobStatus) {
		this.jobStatus = jobStatus;
		return this;
	}
}
