package org.snomed.simplex.rest.pojos;

import java.util.List;

public record RepairTranslationSetSizesResponse(
		int repaired,
		int unchanged,
		int skipped,
		List<RepairTranslationSetSizeChange> changes) {

	public record RepairTranslationSetSizeChange(
			String id,
			String codesystem,
			String refset,
			String label,
			int oldSize,
			int newSize) {
	}
}
