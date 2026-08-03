package org.snomed.simplex.snolate.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConceptListSupport {

	private ConceptListSupport() {
	}

	public static String joinConceptList(List<String> conceptIds) {
		if (conceptIds == null || conceptIds.isEmpty()) {
			return "";
		}
		return String.join(",", conceptIds);
	}

	public static List<String> splitConceptList(String conceptList) {
		if (conceptList == null || conceptList.isBlank()) {
			return List.of();
		}
		List<String> ids = new ArrayList<>();
		for (String part : conceptList.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				ids.add(trimmed);
			}
		}
		return ids;
	}

	public static String normaliseConceptIdCell(String cellValue) {
		if (cellValue == null || cellValue.isBlank()) {
			return null;
		}
		String value = cellValue.trim();
		if (value.contains("|")) {
			value = value.substring(0, value.indexOf('|')).trim();
		}
		if (!isValidConceptId(value)) {
			return null;
		}
		return value;
	}

	public static boolean isValidConceptId(String conceptId) {
		return conceptId != null && conceptId.matches("\\d{6,18}");
	}

	public record ConceptListParseResult(List<String> conceptIds, int invalidRows, int duplicateRows) {
	}

	public static ConceptListParseResult dedupeConceptIds(List<String> rawIds) {
		Set<String> seen = new LinkedHashSet<>();
		List<String> conceptIds = new ArrayList<>();
		int invalidRows = 0;
		int duplicateRows = 0;
		for (String rawId : rawIds) {
			String conceptId = normaliseConceptIdCell(rawId);
			if (conceptId == null) {
				if (rawId != null && !rawId.isBlank()) {
					invalidRows++;
				}
				continue;
			}
			if (!seen.add(conceptId)) {
				duplicateRows++;
				continue;
			}
			conceptIds.add(conceptId);
		}
		return new ConceptListParseResult(conceptIds, invalidRows, duplicateRows);
	}
}
