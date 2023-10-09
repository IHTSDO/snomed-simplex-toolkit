package com.snomed.simplextoolkit.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.ChangeSummary;
import com.snomed.simplextoolkit.service.ProgressMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

import static java.lang.String.format;

public class AsyncJob implements ProgressMonitor {

	private final String id;
	private final Date created;
	private final String display;
	private JobStatus status;
	private int recordsTotal;
	private int recordsProcessed;
	private ChangeSummary changeSummary;
	private ServiceException serviceException;
	private File tempFile;
	private String refsetId;

	public AsyncJob(String display) {
		this.id = UUID.randomUUID().toString();
		this.created = new Date();
		this.display = display;
	}

	public String getDisplayWithStatus() {
		return format("%s (%s)", display, status);
	}

	public String getId() {
		return id;
	}

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

	public int getRecordsTotal() {
		return recordsTotal;
	}

	@Override
	public void setRecordsTotal(int recordsTotal) {
		this.recordsTotal = recordsTotal;
	}

	public int getRecordsProcessed() {
		return recordsProcessed;
	}

	@Override
	public void setRecordsProcessed(int recordsProcessed) {
		this.recordsProcessed = recordsProcessed;
	}

	@Override
	public void setProgressPercentageInsteadOfNumber(int progressPercentage) {
		recordsProcessed = Math.round((recordsTotal * progressPercentage) / 100f);
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

	public ServiceException getServiceException() {
		return serviceException;
	}

	public void setTempFile(File tempFile) {
		this.tempFile = tempFile;
	}

	@JsonIgnore
	public File getTempFile() {
		return tempFile;
	}

	@JsonIgnore
	public InputStream getInputStream() throws ServiceException {
		try {
			return new FileInputStream(tempFile);
		} catch (FileNotFoundException e) {
			throw new ServiceException("Job input stream not found.", e);
		}
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getRefsetId() {
		return refsetId;
	}
}
