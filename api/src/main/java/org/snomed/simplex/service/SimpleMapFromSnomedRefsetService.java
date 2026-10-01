package org.snomed.simplex.service;

import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.domain.RefsetMemberIntent;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapFromSnomed;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class SimpleMapFromSnomedRefsetService extends RefsetUpdateService<RefsetMemberIntentSimpleMapFromSnomed> {

	public SimpleMapFromSnomedRefsetService(SpreadsheetService spreadsheetService, SnowstormClientFactory snowstormClientFactory) {
		super(spreadsheetService, snowstormClientFactory);
	}

	@Override
	protected List<SheetHeader> getInputSheetExpectedHeaders() {
		return SimpleMapServiceHelper.inputSheetExpectedHeaders();
	}

	@Override
	protected SheetRowToComponentIntentExtractor<RefsetMemberIntentSimpleMapFromSnomed> getInputSheetMemberExtractor() {
		return SimpleMapServiceHelper::extractFromSnomedRow;
	}

	@Override
	protected Map<String, Function<RefsetMember, String>> getRefsetToSpreadsheetConversionMap() {
		return SimpleMapServiceHelper.refsetToSpreadsheetConversionMapFromSnomed();
	}

	@Override
	protected RefsetMember convertToMember(RefsetMemberIntent inputMember, String refsetId, String moduleId) {
		return SimpleMapServiceHelper.convertToMember((RefsetMemberIntentSimpleMapFromSnomed) inputMember, refsetId, moduleId);
	}

	@Override
	protected boolean matchMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		return true;
	}

	@Override
	protected boolean applyMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		return SimpleMapServiceHelper.applyMember(wantedRefsetMember, storedMember);
	}
}
