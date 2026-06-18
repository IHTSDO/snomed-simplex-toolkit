package org.snomed.simplex.snolate.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LanguagePolicyQuestion(
		String id,
		String type,
		boolean required,
		String title,
		String hint,
		LanguagePolicyExampleTable exampleTable,
		List<LanguagePolicyOption> options,
		List<String> rowKeys,
		String promptTemplate
) {
}
