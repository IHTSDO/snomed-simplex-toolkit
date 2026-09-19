package org.snomed.simplex.rest.pojos;

import java.util.List;

public record AdminConceptEditorDetail(
		String codeSystem,
		String conceptId,
		boolean conceptActive,
		String moduleId,
		String defaultModuleId,
		String fsnTerm,
		String ptTerm,
		List<AdminConceptDescriptionDto> descriptions,
		List<AdminConceptLangRefsetDto> langRefsets
) {
}
