package org.snomed.simplex.snolate.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LanguagePolicyExampleTable(
		List<String> headers,
		List<List<String>> rows
) {
}
