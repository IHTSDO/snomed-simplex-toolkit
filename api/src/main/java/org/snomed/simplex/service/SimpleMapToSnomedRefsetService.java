package org.snomed.simplex.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.domain.RefsetMemberIntent;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapToSnomed;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.spreadsheet.SheetHeader;
import org.snomed.simplex.service.spreadsheet.SheetRowToComponentIntentExtractor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SimpleMapToSnomedRefsetService extends RefsetUpdateService<RefsetMemberIntentSimpleMapToSnomed> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SimpleMapToSnomedRefsetService(SpreadsheetService spreadsheetService, SnowstormClientFactory snowstormClientFactory) {
		super(spreadsheetService, snowstormClientFactory);
	}

	@Override
	protected List<SheetHeader> getInputSheetExpectedHeaders() {
		return SimpleMapServiceHelper.inputSheetExpectedHeaders();
	}

	@Override
	protected SheetRowToComponentIntentExtractor<RefsetMemberIntentSimpleMapToSnomed> getInputSheetMemberExtractor() {
		return (cells, rowNumber, headerConfiguration) -> {

			String sourceCode = SpreadsheetService.readGenericCode(cells, headerConfiguration.getColumn(SimpleMapServiceHelper.SOURCE_CODE), rowNumber);
			if (sourceCode == null) {
				return null;
			}
			String targetCode = SpreadsheetService.readSnomedConcept(cells, headerConfiguration.getColumn(SimpleMapServiceHelper.TARGET_CODE), rowNumber);
			if (targetCode == null) {
				return null;
			}
			return new RefsetMemberIntentSimpleMapToSnomed(sourceCode, targetCode);
		};
	}

	@Override
	protected Map<String, Function<RefsetMember, String>> getRefsetToSpreadsheetConversionMap() {
		return SimpleMapServiceHelper.refsetToSpreadsheetConversionMapToSnomed(Map.of());
	}

	@Override
	public void downloadRefsetAsSpreadsheet(String refsetId, OutputStream outputStream, CodeSystem codeSystem)
			throws ServiceException, IOException {

		SnowstormClient snowstormClient = getSnowstormClient();
		List<RefsetMember> members = snowstormClient.loadAllRefsetMembers(refsetId, codeSystem, true);
		members.sort(Comparator.comparing(RefsetMember::getReferencedComponentId));

		Set<String> targetConceptIds = members.stream()
				.map(RefsetMember::getReferencedComponentId)
				.filter(Objects::nonNull)
				.filter(id -> !id.isBlank())
				.collect(Collectors.toSet());
		Map<String, String> targetConceptIdToFsn = loadTargetConceptIdToFsn(targetConceptIds, snowstormClient, codeSystem);

		Map<String, Function<RefsetMember, String>> refsetColumns =
				SimpleMapServiceHelper.refsetToSpreadsheetConversionMapToSnomed(targetConceptIdToFsn);
		try (Workbook workbook = getSpreadsheetService().createRefsetSpreadsheet(members, refsetColumns)) {
			workbook.write(outputStream);
		}
	}

	@Override
	protected List<RefsetMemberIntentSimpleMapToSnomed> filterSpreadsheetMembers(
			List<RefsetMemberIntentSimpleMapToSnomed> sheetMembers, CodeSystem codeSystem) throws ServiceException {

		if (sheetMembers.isEmpty()) {
			return sheetMembers;
		}
		Set<String> targetIds = sheetMembers.stream()
				.map(RefsetMemberIntentSimpleMapToSnomed::getReferenceComponentId)
				.collect(Collectors.toSet());
		List<String> existingTargetIds = getSnowstormClient().getConceptIds(targetIds, codeSystem).stream()
				.map(Object::toString)
				.toList();
		List<RefsetMemberIntentSimpleMapToSnomed> filtered = sheetMembers.stream()
				.filter(member -> existingTargetIds.contains(member.getReferenceComponentId()))
				.toList();
		if (filtered.size() < sheetMembers.size()) {
			logger.info("{} map target SNOMED concepts do not exist and will be skipped.", sheetMembers.size() - filtered.size());
		}
		return filtered;
	}

	@Override
	protected RefsetMember convertToMember(RefsetMemberIntent inputMember, String refsetId, String moduleId) {
		return SimpleMapServiceHelper.convertToMember((RefsetMemberIntentSimpleMapToSnomed) inputMember, refsetId, moduleId);
	}

	@Override
	protected boolean matchMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		return Objects.equals(
				wantedRefsetMember.getAdditionalFields().get(SimpleMapServiceHelper.MAP_SOURCE),
				storedMember.getAdditionalFields().get(SimpleMapServiceHelper.MAP_SOURCE));
	}

	@Override
	protected boolean applyMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		return SimpleMapServiceHelper.applyToSnomedMember(wantedRefsetMember, storedMember);
	}

	private Map<String, String> loadTargetConceptIdToFsn(
			Set<String> targetConceptIds, SnowstormClient snowstormClient, CodeSystem codeSystem) {

		if (targetConceptIds.isEmpty()) {
			return Map.of();
		}
		List<Long> conceptIdLongs = targetConceptIds.stream().map(Long::parseLong).toList();
		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIdLongs, codeSystem);
		Map<String, String> targetConceptIdToFsn = new HashMap<>();
		for (Concept concept : concepts) {
			if (concept.getFsn() != null && concept.getFsn().getTerm() != null) {
				targetConceptIdToFsn.put(concept.getConceptId(), concept.getFsn().getTerm());
			}
		}
		return targetConceptIdToFsn;
	}
}
