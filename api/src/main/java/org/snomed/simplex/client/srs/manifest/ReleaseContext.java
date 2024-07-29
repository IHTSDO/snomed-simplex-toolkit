package org.snomed.simplex.client.srs.manifest;

public record ReleaseContext(org.snomed.simplex.client.domain.CodeSystem codeSystem, String effectiveTime,
							 org.snomed.simplex.client.SnowstormClient snowstormClient) {
}
