package com.snomed.simplextoolkit.service.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.snomed.simplextoolkit.domain.JobStatus;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.springframework.security.core.context.SecurityContext;

import java.util.Date;
import java.util.UUID;

import static java.lang.String.format;

public abstract class AsyncJob {

	private final String codeSystem;
	private final String id;
	private final Date created;
	private final String display;
	private JobStatus status;
	private ChangeSummary changeSummary;
	private ServiceException serviceException;
	private String errorMessage;
	private SecurityContext securityContext;

	public AsyncJob(String codeSystem, String display) {
		this.codeSystem = codeSystem;
		this.id = UUID.randomUUID().toString();
		this.created = new Date();
		this.display = display;
	}

	public String getDisplayWithStatus() {
		return format("%s (%s)", display, status);
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getId() {
		return id;
	}

	public abstract JobType getJobType();

	public Date getCreated() {
		return created;
	}

	public String getDisplay() {
		return display;
	}

	public JobStatus getStatus() {
		return status;
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	public ChangeSummary getChangeSummary() {
		return changeSummary;
	}

	public void setChangeSummary(ChangeSummary changeSummary) {
		this.changeSummary = changeSummary;
	}

	public void setServiceException(ServiceException serviceException) {
		this.serviceException = serviceException;
	}

	@JsonIgnore
	public ServiceException getServiceException() {
		return serviceException;
	}

	public String getErrorMessage() {
		if (errorMessage == null && serviceException != null) {
			return serviceException.getMessage();
		}
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public void setSecurityContext(SecurityContext securityContext) {
		this.securityContext = securityContext;
	}

	@JsonIgnore
	public SecurityContext getSecurityContext() {
		return securityContext;
	}
}
