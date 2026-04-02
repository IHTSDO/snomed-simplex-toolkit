package org.snomed.simplex.snolate.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "translation_source")
public class TranslationSource {

	@Id
	@Column(nullable = false, length = 18)
	private String code;

	@Column(nullable = false, length = 255)
	private String term;

	@Column(name = "display_order", nullable = false)
	private int order;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "translation_source_set", joinColumns = @JoinColumn(name = "translation_source_code"),
			indexes = @Index(name = "idx_translation_source_set_set_code", columnList = "set_code"))
	@Column(name = "set_code", nullable = false, length = 512)
	private Set<String> sets = new LinkedHashSet<>();

	protected TranslationSource() {
	}

	public TranslationSource(String code, String term, int order) {
		this.code = code;
		this.term = term;
		this.order = order;
		this.sets = new LinkedHashSet<>();
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

	public Set<String> getSets() {
		return sets;
	}

	public void setSets(Set<String> sets) {
		this.sets = sets != null ? new LinkedHashSet<>(sets) : new LinkedHashSet<>();
	}
}
