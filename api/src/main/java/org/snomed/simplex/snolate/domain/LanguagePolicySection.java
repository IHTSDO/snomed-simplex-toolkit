package org.snomed.simplex.snolate.domain;

import java.util.List;

public record LanguagePolicySection(
		String id,
		String label,
		List<LanguagePolicyQuestion> questions
) {
}
