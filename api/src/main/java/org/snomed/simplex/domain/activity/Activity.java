package org.snomed.simplex.domain.activity;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

// A user or system activity log entry
@Document(indexName = "#{@indexNameProvider.indexName('activity')}")
public class Activity {

	@Id
	private String id;
	private final String user;
	private final String codesystem;
	private final ComponentType componentType;
	private final ActivityType activityType;
	private final Date startDate;
	private String componentId;
	private String fileUpload;
	private Date endDate;
	private boolean error;
	private String message;
	private String stackTrace;

	public Activity(String codesystem, ComponentType componentType, ActivityType activityType) {
		this(SecurityUtil.getUsername(), codesystem, componentType, activityType);
	}

	public Activity(String user, String codesystem, ComponentType componentType, ActivityType activityType) {
		this.user = user;
		this.codesystem = codesystem;
		this.componentType = componentType;
		this.activityType = activityType;
		startDate = new Date();
	}

	public void exception(ServiceException e) {
		setError(true);
		setMessage(e.getMessage());
		setStackTrace(getStackTraceAsString(e));
	}

	private String getStackTraceAsString(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}

	public void end() {
		endDate = new Date();
	}

	public String getUser() {
		return user;
	}

	public String getCodesystem() {
		return codesystem;
	}

	public ComponentType getComponentType() {
		return componentType;
	}

	public ActivityType getActivityType() {
		return activityType;
	}

	public String getComponentId() {
		return componentId;
	}

	public Date getStartDate() {
		return startDate;
	}

	public String getFileUpload() {
		return fileUpload;
	}

	public Date getEndDate() {
		return endDate;
	}

	public boolean isError() {
		return error;
	}

	public String getMessage() {
		return message;
	}

	public void setComponentId(String componentId) {
		this.componentId = componentId;
	}

	public void setFileUpload(String fileUpload) {
		this.fileUpload = fileUpload;
	}

	public void setError(boolean error) {
		this.error = error;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}
}
