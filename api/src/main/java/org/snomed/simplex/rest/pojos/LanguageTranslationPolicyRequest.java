package org.snomed.simplex.rest.pojos;

import java.util.List;
import java.util.Map;

public record LanguageTranslationPolicyRequest(
		String questionnaireVersion,
		String languageDialectName,
		Map<String, String> policyItems,
		List<String> selectedRules
) {
}
