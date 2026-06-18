package org.snomed.simplex.rest.pojos;

import java.util.Map;

public record AiSetupRequest(Map<String, String> aiGoldenSet) {
}
