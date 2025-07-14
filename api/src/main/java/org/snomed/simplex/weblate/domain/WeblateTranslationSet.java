package org.snomed.simplex.weblate.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

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

	@Field(type = FieldType.Text)
	private final String label;

	@Field(type = FieldType.Text)
	private final String ecl;

	@Field(type = FieldType.Text)
	private final String branchPath;

	private TranslationSetStatus status;

	@Transient
	private String weblateLabelUrl;

	public WeblateTranslationSet(String codesystem, String refset, String name, String label, String ecl, String branchPath) {
		this.codesystem = codesystem;
		this.refset = refset;
		this.name = name;
		this.label = label;
		this.ecl = ecl;
		this.branchPath = branchPath;
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

	public String getBranchPath() {
		return branchPath;
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

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (WeblateTranslationSet) obj;
		return Objects.equals(this.codesystem, that.codesystem) &&
				Objects.equals(this.refset, that.refset) &&
				Objects.equals(this.name, that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(codesystem, refset, name);
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
