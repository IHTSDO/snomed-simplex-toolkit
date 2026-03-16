package org.snomed.simplex.exceptions;

import org.snomed.simplex.domain.JobStatus;
import org.springframework.http.HttpStatus;

/**
 * Service exception carrying an HTTP status code and an optional {@link JobStatus}.
 *
 * <p><strong>System alert behaviour</strong> — when this exception is caught by a content
 * processing job, the {@code jobStatus} field controls whether a system alert is raised via the
 * {@code SupportRegister}:</p>
 * <ul>
 *   <li>{@code jobStatus} not set (null) — defaults to {@link JobStatus#SYSTEM_ERROR},
 *       which <em>raises a system alert</em>.</li>
 *   <li>{@link JobStatus#SYSTEM_ERROR} — <em>raises a system alert</em>.</li>
 *   <li>{@link JobStatus#TECHNICAL_CONTENT_ISSUE} — <em>raises a system alert</em>.</li>
 *   <li>{@link JobStatus#USER_CONTENT_ERROR} or {@link JobStatus#USER_CONTENT_WARNING} —
 *       no system alert is raised; the error is surfaced to the user only.</li>
 * </ul>
 * <p>Use one of the constructors that accepts a {@link JobStatus} argument whenever the error
 * is caused by user content rather than a system fault, so that no spurious alert is raised.</p>
 */
public class ServiceExceptionWithStatusCode extends ServiceException {

	private final int statusCode;
	private final JobStatus jobStatus;

	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode, JobStatus jobStatus, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode.value();
		this.jobStatus = jobStatus;
	}

	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode, JobStatus jobStatus) {
		this(message, statusCode, jobStatus, null);
	}

	/** Convenience constructor — leaves {@code jobStatus} null, which defaults to {@link JobStatus#SYSTEM_ERROR} and raises a system alert. */
	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode) {
		this(message, statusCode, null, null);
	}

	/** Convenience constructor — leaves {@code jobStatus} null, which defaults to {@link JobStatus#SYSTEM_ERROR} and raises a system alert. */
	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode, Throwable cause) {
		this(message, statusCode, null, cause);
	}

	public int getStatusCode() {
		return statusCode;
	}

	public JobStatus getJobStatus() {
		return jobStatus;
	}

}
