package org.snomed.simplex.rest.pojos;

import java.util.Map;

public record AiSetupRequest(String languageAdvice, Map<String, String> aiGoldenSet) {
}
