package org.snomed.simplex.rest.pojos;

import java.util.List;
import java.util.Map;

public record CustomConceptRequest(
		String parentCode,
		boolean active,
		String conceptCode,
		Map<String, List<String>> langRefsetTerms
) {
}
