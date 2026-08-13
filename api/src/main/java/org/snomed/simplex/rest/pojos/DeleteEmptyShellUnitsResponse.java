package org.snomed.simplex.rest.pojos;

import java.util.List;

public record DeleteEmptyShellUnitsResponse(
		int languageRefsetsProcessed,
		int deleted,
		List<DeleteEmptyShellUnitsByRefset> byRefset,
		RepairTranslationSetSizesResponse setSizeRepair) {

	public record DeleteEmptyShellUnitsByRefset(
			String refsetId,
			String languageCode,
			int deleted) {
	}
}
