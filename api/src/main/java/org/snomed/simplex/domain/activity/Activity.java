package org.snomed.simplex.domain.activity;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.util.ExceptionUtil;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.text.SimpleDateFormat;
import java.util.Date;

// A user or system activity log entry
@Document(indexName = "#{@indexNameProvider.indexName('activity')}")
public class Activity {

	public static final String AUTOMATIC = "AUTOMATIC";

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
	private String startDateHuman;

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
		setStartDate(new Date());
	}

	public void exception(ServiceException e) {
		setError(true);
		setMessage(e.getMessage());
		setStackTrace(ExceptionUtil.getStackTraceAsString(e));
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
		this.startDateHuman = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(startDate);
	}

	public void end() {
		end(new Date());
	}

	public void end(Date endDate) {
		this.endDate = endDate;
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

	public String getStartDateHuman() {
		return startDateHuman;
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
		StringBuilder builder = new StringBuilder("Activity{");
		builder.append("codesystem='").append(codesystem).append('\'');
		builder.append(", componentType=").append(componentType);
		builder.append(", activityType=").append(activityType);
		if (componentId != null) {
			builder.append(", componentId='").append(componentId).append('\'');
		}
		builder.append(", user='").append(user).append('\'');
		builder.append(", startDateHuman='").append(startDateHuman).append('\'');
		builder.append('}');
		return builder.toString();
	}
}
