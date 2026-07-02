package org.snomed.simplex.ai;

public record LlmUsageRecord(String codesystem, String model, String provider, int inputTokens, int outputTokens) {
}
