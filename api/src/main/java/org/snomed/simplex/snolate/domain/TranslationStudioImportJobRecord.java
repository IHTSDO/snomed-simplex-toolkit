package org.snomed.simplex.snolate.domain;

import org.snomed.simplex.domain.JobStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document(indexName = "#{@indexNameProvider.indexName('translation-studio-import-job')}")
public class TranslationStudioImportJobRecord {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String codesystem;

	@Field(type = FieldType.Keyword)
	private String refsetId;

	@Field(type = FieldType.Keyword)
	private String display;

	@Field(type = FieldType.Keyword)
	private String username;

	@Field(type = FieldType.Long)
	private Date created;

	@Field(type = FieldType.Keyword)
	private JobStatus status;

	@Field(type = FieldType.Text)
	private String errorMessage;

	@Field(type = FieldType.Integer)
	private int updated;

	@Field(type = FieldType.Integer)
	private int skippedNotFound;

	@Field(type = FieldType.Integer)
	private int skippedOutsideSet;

	@Field(type = FieldType.Keyword)
	private List<String> skippedNotFoundCodes = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCodesystem() {
		return codesystem;
	}

	public void setCodesystem(String codesystem) {
		this.codesystem = codesystem;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Date getCreated() {
		return created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}

	public JobStatus getStatus() {
		return status;
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public int getUpdated() {
		return updated;
	}

	public void setUpdated(int updated) {
		this.updated = updated;
	}

	public int getSkippedNotFound() {
		return skippedNotFound;
	}

	public void setSkippedNotFound(int skippedNotFound) {
		this.skippedNotFound = skippedNotFound;
	}

	public int getSkippedOutsideSet() {
		return skippedOutsideSet;
	}

	public void setSkippedOutsideSet(int skippedOutsideSet) {
		this.skippedOutsideSet = skippedOutsideSet;
	}

	public List<String> getSkippedNotFoundCodes() {
		return skippedNotFoundCodes;
	}

	public void setSkippedNotFoundCodes(List<String> skippedNotFoundCodes) {
		this.skippedNotFoundCodes = skippedNotFoundCodes != null ? skippedNotFoundCodes : new ArrayList<>();
	}
}
