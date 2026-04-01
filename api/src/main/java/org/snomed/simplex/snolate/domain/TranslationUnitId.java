package org.snomed.simplex.snolate.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link TranslationUnit}. {@link #languageCode} stores the composite Weblate-style key
 * {@code "%s-%s".formatted(isoLanguageCode, refsetId)}.
 */
public class TranslationUnitId implements Serializable {

	private String code;
	private String languageCode;

	public TranslationUnitId() {
	}

	public TranslationUnitId(String code, String languageCode) {
		this.code = code;
		this.languageCode = languageCode;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TranslationUnitId that = (TranslationUnitId) o;
		return Objects.equals(code, that.code) && Objects.equals(languageCode, that.languageCode);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, languageCode);
	}
}
