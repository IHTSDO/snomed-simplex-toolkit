package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.domain.CodeSystemProperties;
import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.domain.SheetHeader;
import com.snomed.derivativemanagementtool.domain.SheetRefsetMember;
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
	protected SheetRowToRefsetExtractor getInputSheetMemberExtractor() {
		return (cells, rowNumber, headerConfiguration) -> {
			Integer conceptCodeColumn = headerConfiguration.getColumn(CONCEPT_CODE);
			String cellValue = SpreadsheetService.readSnomedConcept(cells, conceptCodeColumn, rowNumber);
			if (cellValue != null) {
				return new SheetRefsetMember(cellValue);
			}
			return null;
		};
	}

	@Override
	protected RefsetMember convertToMember(String refsetId, CodeSystemProperties config, SheetRefsetMember inputMember) {
		return new RefsetMember(refsetId, config.getDefaultModule(), inputMember.getReferenceComponentId());
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
