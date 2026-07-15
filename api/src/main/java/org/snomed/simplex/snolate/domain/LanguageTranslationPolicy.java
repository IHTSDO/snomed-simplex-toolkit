package org.snomed.simplex.snolate.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document(indexName = "#{@indexNameProvider.indexName('snolate-language-policy')}")
public class LanguageTranslationPolicy {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String codesystem;

	@Field(type = FieldType.Keyword)
	private String refset;

	@Field(type = FieldType.Keyword)
	private String languageCode;

	@Field(type = FieldType.Keyword)
	private String displayName;

	@Field(type = FieldType.Keyword)
	private String questionnaireVersion;

	@Field(type = FieldType.Object)
	private LinkedHashMap<String, String> policyItems;

	@Field(type = FieldType.Keyword)
	private List<String> selectedRules;

	@Field(type = FieldType.Long)
	private Date created;

	@Field(type = FieldType.Long)
	private Date lastModified;

	public static String compositeId(String codesystem, String refset) {
		return "%s_%s".formatted(codesystem, refset);
	}

	public LanguageTranslationPolicy() {
		// Empty constructor for Jackson deserialization
	}

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

	public String getRefset() {
		return refset;
	}

	public void setRefset(String refset) {
		this.refset = refset;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getQuestionnaireVersion() {
		return questionnaireVersion;
	}

	public void setQuestionnaireVersion(String questionnaireVersion) {
		this.questionnaireVersion = questionnaireVersion;
	}

	public Map<String, String> getPolicyItems() {
		return policyItems;
	}

	public void setPolicyItems(Map<String, String> policyItems) {
		this.policyItems = policyItems != null ? new LinkedHashMap<>(policyItems) : null;
	}

	public List<String> getSelectedRules() {
		return selectedRules;
	}

	public void setSelectedRules(List<String> selectedRules) {
		this.selectedRules = selectedRules != null ? new ArrayList<>(selectedRules) : null;
	}

	public Date getCreated() {
		return created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}

	public Date getLastModified() {
		return lastModified;
	}

	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
}
