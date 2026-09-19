package org.snomed.simplex.rest.pojos;

import java.util.List;

public record AdminConceptUpdateRequest(List<AdminConceptDescriptionDto> descriptions) {
}
