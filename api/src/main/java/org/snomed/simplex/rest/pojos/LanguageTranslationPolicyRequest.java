package org.snomed.simplex.rest.pojos;

import java.util.Map;

public record LanguageTranslationPolicyRequest(
		String questionnaireVersion,
		Map<String, String> policyItems
) {
}
