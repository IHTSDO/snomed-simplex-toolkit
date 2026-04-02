package org.snomed.simplex.snolate.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "translation_unit", indexes = {
		@Index(name = "idx_translation_unit_language_code", columnList = "language_code")
})
@IdClass(TranslationUnitId.class)
public class TranslationUnit {

	@Id
	@Column(nullable = false, length = 18)
	private String code;

	/**
	 * Composite language key
	 * {@code "%s-%s".formatted(isoLanguageCode, refsetId)}.
	 */
	@Id
	@Column(name = "language_code", nullable = false, length = 128)
	private String languageCode;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "translation_unit_term", joinColumns = {
			@JoinColumn(name = "translation_unit_code", referencedColumnName = "code"),
			@JoinColumn(name = "language_code", referencedColumnName = "language_code") },
			indexes = @Index(name = "idx_translation_unit_term_unit_lang", columnList = "translation_unit_code,language_code"))
	@OrderColumn(name = "term_index")
	@Column(name = "term", nullable = false, length = 4000)
	private List<String> terms = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TranslationStatus status;

	protected TranslationUnit() {
	}

	public TranslationUnit(String code, String languageCode, List<String> terms, TranslationStatus status) {
		this.code = code;
		this.languageCode = languageCode;
		this.terms = terms != null ? new ArrayList<>(terms) : new ArrayList<>();
		this.status = status;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public List<String> getTerms() {
		return terms;
	}

	public void setTerms(List<String> terms) {
		this.terms = terms != null ? new ArrayList<>(terms) : new ArrayList<>();
	}

	public TranslationStatus getStatus() {
		return status;
	}

	public void setStatus(TranslationStatus status) {
		this.status = status;
	}
}
