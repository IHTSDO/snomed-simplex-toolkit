package org.snomed.simplex.snolate.domain;

import java.util.List;

public record LanguagePolicyQuestionnaire(
		String version,
		String title,
		List<LanguagePolicySection> sections
) {
}
