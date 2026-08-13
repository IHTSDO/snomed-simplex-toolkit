package org.snomed.simplex.rest.pojos;

import java.util.List;
import java.util.Map;

public record CustomConceptDetail(
		String conceptId,
		boolean active,
		String parentCode,
		String parentTerm,
		Map<String, List<String>> langRefsetTerms,
		List<CustomConceptLangRefset> langRefsets
) {
}
