package org.snomed.simplex.snolate.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LanguagePolicyOption(
		String value,
		String label,
		String prompt,
		String promptTemplate,
		Boolean allowFreeText,
		String freeTextKey
) {
}
