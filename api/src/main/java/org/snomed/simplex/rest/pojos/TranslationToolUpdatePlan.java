package org.snomed.simplex.rest.pojos;

import org.snomed.simplex.client.domain.CodeSystemVersion;

public record TranslationToolUpdatePlan(Integer currentVersion, Integer newVersionDate, CodeSystemVersion newVersion, org.snomed.simplex.client.domain.CodeSystem codeSystem) {
}
