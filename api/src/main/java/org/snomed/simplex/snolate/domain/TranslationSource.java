package org.snomed.simplex.snolate.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "#{@indexNameProvider.indexName('snolate-translation-source')}")
public class TranslationSource {

	@Id
	private String code;

	@Field(type = FieldType.Keyword)
	private String term;

	@Field(type = FieldType.Integer)
	private int order;

	public TranslationSource() {
	}

	public TranslationSource(String code, String term, int order) {
		this.code = code;
		this.term = term;
		this.order = order;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}
}
