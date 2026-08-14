package org.snomed.simplex.service.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.security.core.context.SecurityContext;

import java.util.Date;
import java.util.UUID;

import static java.lang.String.format;

public abstract class AsyncJob {

	private final CodeSystem codeSystem;
	private final String codeSystemShortName;
	private final String id;
	private final Date created;
	private final String display;
	private JobStatus status;
	private ChangeSummary changeSummary;
	private ServiceException serviceException;
	private String errorMessage;
	private String username;
	private SecurityContext securityContext;

	protected AsyncJob(CodeSystem codeSystem, String display) {
		this(codeSystem, null, display, UUID.randomUUID().toString(), new Date());
	}

	protected AsyncJob(CodeSystem codeSystem, String codeSystemShortName, String display, String id, Date created) {
		this.codeSystem = codeSystem;
		this.codeSystemShortName = codeSystemShortName;
		this.id = id;
		this.created = created;
		this.display = display;
	}

	public boolean isQueuedOrInProgress() {
		return status == JobStatus.QUEUED || status == JobStatus.IN_PROGRESS;
	}

	public String getDisplayWithStatus() {
		return format("%s (%s)", display, status);
	}

	public String getCodeSystem() {
		if (codeSystemShortName != null) {
			return codeSystemShortName;
		}
		return codeSystem != null ? codeSystem.getShortName() : null;
	}

	@JsonIgnore
	public CodeSystem getCodeSystemObject() {
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

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setSecurityContext(SecurityContext securityContext) {
		this.securityContext = securityContext;
	}

	@JsonIgnore
	public SecurityContext getSecurityContext() {
		return securityContext;
	}

	@Override
	public String toString() {
		return "AsyncJob{" +
				"id='" + id + '\'' +
				", created=" + created +
				", display='" + display + '\'' +
				", status=" + status +
				", codeSystem=" + codeSystem +
				'}';
	}
}
