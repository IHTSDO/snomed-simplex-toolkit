package org.snomed.simplex.rest.pojos;

import java.util.Map;

public record AdminConceptDescriptionDto(
		String descriptionId,
		String term,
		String type,
		String lang,
		boolean active,
		boolean released,
		String moduleId,
		String caseSignificance,
		Map<String, String> acceptabilityMap
) {
}
