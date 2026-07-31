package org.snomed.simplex.rest.pojos;

import java.util.List;

public record RepairTranslationUnitIdsResponse(
		int compositeLanguageBucketsProcessed,
		int mergedGroups,
		int orphansDeleted,
		int unchanged,
		List<String> warnings) {
}
