package org.snomed.simplex.domain.activity;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

// A user or system activity log entry
@Document(indexName = "#{@indexNameProvider.indexName('activity')}")
public class Activity {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String user;

	@Field(type = FieldType.Keyword)
	private String codesystem;

	@Field(type = FieldType.Keyword)
	private ComponentType componentType;

	@Field(type = FieldType.Keyword)
	private ActivityType activityType;

	@Field(type = FieldType.Long)
	private Date startDate;

	@Field(type = FieldType.Keyword)
	private String componentId;

	@Field(type = FieldType.Keyword)
	private String fileUpload;

	@Field(type = FieldType.Long)
	private Date endDate;

	@Field(type = FieldType.Boolean)
	private boolean error;

	@Field(type = FieldType.Text)
	private String message;

	@Field(type = FieldType.Text)
	private String stackTrace;

	public Activity() {
	}

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

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
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

	@Override
	public String toString() {
		return "Activity{" +
				"codesystem='" + codesystem + '\'' +
				", componentType=" + componentType +
				", activityType=" + activityType +
				", componentId='" + componentId + '\'' +
				", user='" + user + '\'' +
				'}';
	}
}
