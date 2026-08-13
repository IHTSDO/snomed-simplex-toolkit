package org.snomed.simplex.rest.pojos;

import org.snomed.simplex.service.job.ChangeSummary;

public record CustomConceptSaveResponse(String conceptId, ChangeSummary changeSummary) {
}
