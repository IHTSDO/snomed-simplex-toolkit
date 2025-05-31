package org.snomed.simplex.weblate.domain;

import org.springframework.data.annotation.Id;
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
	private final String name;

	@Field(type = FieldType.Text)
	private final String label;

	@Field(type = FieldType.Text)
	private final String ecl;

	public WeblateTranslationSet(String codesystem, String refset, String name, String label, String ecl) {
		this.codesystem = codesystem;
		this.refset = refset;
		this.name = name;
		this.label = label;
		this.ecl = ecl;
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

	public String getName() {
		return name;
	}

	public String getLabel() {
		return label;
	}

	public String getEcl() {
		return ecl;
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
