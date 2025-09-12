package org.snomed.simplex.weblate.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Document(indexName = "#{@indexNameProvider.indexName('weblate-set')}")
public final class WeblateTranslationSet {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String codesystem;

	@Field(type = FieldType.Keyword)
	private String refset;

	@Field(type = FieldType.Keyword)
	private String languageCode;

	@Field(type = FieldType.Keyword)
	private final String name;

	@Field(type = FieldType.Keyword)
	private final String label;

	@Field(type = FieldType.Keyword)
	private final String ecl;

	@Field(type = FieldType.Keyword)
	private final TranslationSubsetType subsetType;

	@Field(type = FieldType.Keyword)
	private final String selectionCodesystem;

	@Field(type = FieldType.Object)
	private LinkedHashMap<String, String> aiGoldenSet;

	@Field(type = FieldType.Keyword)
	private String aiLanguageAdvice;

	@Field(type = FieldType.Integer)
	private int size;

	@Field(type = FieldType.Integer)
	private int percentageProcessed;

	private TranslationSetStatus status;

	@Field(type = FieldType.Long)
	private Date created;

	@Field(type = FieldType.Long)
	private Date lastPulled;

	@Transient
	private String weblateLabelUrl;

	@Transient
	private int translated;

	@Transient
	private int changedSinceCreatedOrLastPulled;

	public WeblateTranslationSet(String codesystem, String refset, String name, String label,
		String ecl, TranslationSubsetType subsetType, String selectionCodesystem) {

		this.codesystem = codesystem;
		this.refset = refset;
		this.name = name;
		this.label = label;
		this.ecl = ecl;
		this.subsetType = subsetType;
		this.selectionCodesystem = selectionCodesystem;
		this.percentageProcessed = 0;
		this.created = new Date();
	}

	public String getLanguageCodeWithRefsetId() {
		return "%s-%s".formatted(languageCode, refset);
	}

	public String getCompositeLabel() {
		return "%s_%s_%s".formatted(getCodesystem().replace("SNOMEDCT-", ""), getRefset(), getLabel());
	}

	public String getId() {
		return id;
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

	public String getName() {
		return name;
	}

	public String getLabel() {
		return label;
	}

	public String getEcl() {
		return ecl;
	}

	public TranslationSubsetType getSubsetType() {
		return subsetType;
	}

	public String getSelectionCodesystem() {
		return selectionCodesystem;
	}

	public void setAiGoldenSet(Map<String, String> aiGoldenSet) {
		this.aiGoldenSet = new LinkedHashMap<>(aiGoldenSet);
	}

	public Map<String, String> getAiGoldenSet() {
		return aiGoldenSet;
	}

	public String getAiLanguageAdvice() {
		return aiLanguageAdvice;
	}

	public void setAiLanguageAdvice(String aiLanguageAdvice) {
		this.aiLanguageAdvice = aiLanguageAdvice;
	}

	public void setStatus(TranslationSetStatus status) {
		this.status = status;
	}

	public TranslationSetStatus getStatus() {
		return status;
	}

	public String getWeblateLabelUrl() {
		return weblateLabelUrl;
	}

	public void setWeblateLabelUrl(String weblateLabelUrl) {
		this.weblateLabelUrl = weblateLabelUrl;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public int getSize() {
		return size;
	}

	public int getPercentageProcessed() {
		return percentageProcessed;
	}

	public void setPercentageProcessed(int percentageProcessed) {
		this.percentageProcessed = percentageProcessed;
	}

	public void setTranslated(int translated) {
		this.translated = translated;
	}

	public int getTranslated() {
		return translated;
	}

	public Date getCreated() {
		return created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}

	public Date getLastPulled() {
		return lastPulled;
	}

	public void setLastPulled(Date lastPulled) {
		this.lastPulled = lastPulled;
	}

	public int getChangedSinceCreatedOrLastPulled() {
		return changedSinceCreatedOrLastPulled;
	}

	public void setChangedSinceCreatedOrLastPulled(int changedSinceCreatedOrLastPulled) {
		this.changedSinceCreatedOrLastPulled = changedSinceCreatedOrLastPulled;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		WeblateTranslationSet set = (WeblateTranslationSet) o;
		return Objects.equals(id, set.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "WeblateTranslationSet[" +
				"codesystem=" + codesystem + ", " +
				"refset=" + refset + ", " +
				"name=" + name + ", " +
				"label=" + label + ", " +
				"ecl=" + ecl + ']';
	}
}
