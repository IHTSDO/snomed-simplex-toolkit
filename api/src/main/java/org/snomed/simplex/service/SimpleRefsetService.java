package org.snomed.simplex.service;

import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.domain.RefsetMemberIntent;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class SimpleRefsetService extends RefsetUpdateService {

	public static final String CONCEPT_CODE = "Concept code";
	public static final String CONCEPT_DISPLAY = "Concept display";

	@Override
	protected Map<String, Function<RefsetMember, String>> getRefsetToSpreadsheetConversionMap() {
		Map<String, Function<RefsetMember, String>> simpleRefsetColumns = new LinkedHashMap<>();
		simpleRefsetColumns.put(CONCEPT_CODE, RefsetMember::getReferencedComponentId);
		simpleRefsetColumns.put(CONCEPT_DISPLAY, RefsetMember::getReferencedComponentFsnOrBlank);
		return simpleRefsetColumns;
	}

	@Override
	protected List<SheetHeader> getInputSheetExpectedHeaders() {
		return List.of(new SheetHeader(CONCEPT_CODE));
	}

	@Override
	protected SheetRowToComponentIntentExtractor getInputSheetMemberExtractor() {
		return (cells, rowNumber, headerConfiguration) -> {
			Integer conceptCodeColumn = headerConfiguration.getColumn(CONCEPT_CODE);
			String cellValue = SpreadsheetService.readSnomedConcept(cells, conceptCodeColumn, rowNumber);
			if (cellValue != null) {
				return new RefsetMemberIntent(cellValue);
			}
			return null;
		};
	}

	@Override
	protected RefsetMember convertToMember(RefsetMemberIntent inputMember, String refsetId, String moduleId) {
		return new RefsetMember(refsetId, moduleId, inputMember.getReferenceComponentId());
	}

	@Override
	protected boolean matchMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		return true;
	}

	@Override
	protected boolean applyMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		boolean changed = false;
		if (!storedMember.isActive()) {
			storedMember.setActive(true);
			changed = true;
		}
		return changed;
	}

}
