package org.snomed.simplex.service;

import org.apache.poi.ss.usermodel.Row;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapFromSnomed;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapToSnomed;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.spreadsheet.HeaderConfiguration;
import org.snomed.simplex.service.spreadsheet.SheetHeader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class SimpleMapServiceHelper {

	public static final String SOURCE_CODE = "Source code";
	public static final String SOURCE_DISPLAY = "Source display";
	public static final String TARGET_CODE = "Target code";
	public static final String TARGET_DISPLAY = "Target display";
	public static final String MAP_TARGET = "mapTarget";
	public static final String MAP_SOURCE = "mapSource";

	private SimpleMapServiceHelper() {
	}

	public static List<SheetHeader> inputSheetExpectedHeaders() {
		return List.of(
				new SheetHeader(SOURCE_CODE),
				new SheetHeader(TARGET_CODE));
	}

	public static RefsetMemberIntentSimpleMapFromSnomed extractFromSnomedRow(
			Row cells, Integer rowNumber, HeaderConfiguration headerConfiguration) throws ServiceException {

		String sourceCode = SpreadsheetService.readSnomedConcept(cells, headerConfiguration.getColumn(SOURCE_CODE), rowNumber);
		if (sourceCode == null) {
			return null;
		}
		String targetCode = SpreadsheetService.readGenericCode(cells, headerConfiguration.getColumn(TARGET_CODE), rowNumber);
		if (targetCode == null) {
			return null;
		}
		return new RefsetMemberIntentSimpleMapFromSnomed(sourceCode, targetCode);
	}

	public static Map<String, Function<RefsetMember, String>> refsetToSpreadsheetConversionMapFromSnomed() {
		Map<String, Function<RefsetMember, String>> columns = new LinkedHashMap<>();
		columns.put(SOURCE_CODE, RefsetMember::getReferencedComponentId);
		columns.put(SOURCE_DISPLAY, RefsetMember::getReferencedComponentFsnOrBlank);
		columns.put(TARGET_CODE, member -> member.getAdditionalFields().get(MAP_TARGET));
		columns.put(TARGET_DISPLAY, member -> "");
		return columns;
	}

	public static Map<String, Function<RefsetMember, String>> refsetToSpreadsheetConversionMapToSnomed(
			Map<String, String> targetConceptIdToFsn) {

		Map<String, Function<RefsetMember, String>> columns = new LinkedHashMap<>();
		columns.put(SOURCE_CODE, member -> member.getAdditionalFields().get(MAP_SOURCE));
		columns.put(SOURCE_DISPLAY, member -> "");
		columns.put(TARGET_CODE, RefsetMember::getReferencedComponentId);
		columns.put(TARGET_DISPLAY, member -> targetConceptIdToFsn.getOrDefault(member.getReferencedComponentId(), ""));
		return columns;
	}

	public static RefsetMember convertToMember(RefsetMemberIntentSimpleMapFromSnomed mapInputMember, String refsetId, String moduleId) {
		return new RefsetMember(refsetId, moduleId, mapInputMember.getReferenceComponentId())
				.setAdditionalField(MAP_TARGET, mapInputMember.getMapTarget());
	}

	public static RefsetMember convertToMember(RefsetMemberIntentSimpleMapToSnomed mapInputMember, String refsetId, String moduleId) {
		return new RefsetMember(refsetId, moduleId, mapInputMember.getReferenceComponentId())
				.setAdditionalField(MAP_SOURCE, mapInputMember.getMapSource());
	}

	public static boolean applyToSnomedMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		boolean changed = false;
		String newValue = wantedRefsetMember.getAdditionalFields().get(MAP_SOURCE);
		String oldValue = storedMember.getAdditionalFields().put(MAP_SOURCE, newValue);
		if (!Objects.equals(oldValue, newValue)) {
			changed = true;
		}
		if (!storedMember.isActive()) {
			storedMember.setActive(true);
			changed = true;
		}
		return changed;
	}

	public static boolean applyMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		boolean changed = false;
		String newValue = wantedRefsetMember.getAdditionalFields().get(MAP_TARGET);
		String oldValue = storedMember.getAdditionalFields().put(MAP_TARGET, newValue);
		if (!Objects.equals(oldValue, newValue)) {
			changed = true;
		}
		if (!storedMember.isActive()) {
			storedMember.setActive(true);
			changed = true;
		}
		return changed;
	}
}
