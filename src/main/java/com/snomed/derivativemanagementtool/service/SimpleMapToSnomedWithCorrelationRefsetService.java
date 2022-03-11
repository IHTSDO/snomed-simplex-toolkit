package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.domain.*;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;

import static com.snomed.derivativemanagementtool.service.SpreadsheetService.readGenericCode;
import static com.snomed.derivativemanagementtool.service.SpreadsheetService.readSnomedConcept;

@Service
public class SimpleMapToSnomedWithCorrelationRefsetService extends RefsetUpdateService {

	public static final String SOURCE_CODE = "Source code";
	public static final String SOURCE_DISPLAY = "Source display";
	public static final String TARGET_CODE = "Target code";
	public static final String TARGET_DISPLAY = "Target display";
	public static final String RELATIONSHIP_TYPE_CODE = "Relationship type code";
	public static final String NO_MAP_FLAG = "No map flag";
	public static final String STATUS = "Status";
	public static final String ACCEPTED = "ACCEPTED";
	public static final String MAP_SOURCE = "mapSource";
	public static final String CORRELATION_ID = "correlationId";
	public static final String NO_SCT_MAP_TARGET_CONCEPT = "1193545001";// 1193545001 |No SNOMED CT map target (foundation metadata concept)|

	@Override
	protected List<SheetHeader> getInputSheetExpectedHeaders() {
		return List.of(
				new SheetHeader(SOURCE_CODE),
				new SheetHeader(TARGET_CODE),
				new SheetHeader(RELATIONSHIP_TYPE_CODE),
				new SheetHeader(NO_MAP_FLAG).optional(),// Snap2SNOMED
				new SheetHeader(STATUS).optional());// Snap2SNOMED
	}

	@Override
	protected SheetRowToRefsetExtractor getInputSheetMemberExtractor() {
		return (cells, rowNumber, headerConfiguration) -> {

			String sourceCode = readGenericCode(cells, headerConfiguration.getColumn(SOURCE_CODE), rowNumber);
			String targetCode = readSnomedConcept(cells, headerConfiguration.getColumn(TARGET_CODE), rowNumber);
			String relationshipType = readGenericCode(cells, headerConfiguration.getColumn(RELATIONSHIP_TYPE_CODE), rowNumber);
			String noMapFlag = readGenericCode(cells, headerConfiguration.getColumn(NO_MAP_FLAG), rowNumber);
			String status = readGenericCode(cells, headerConfiguration.getColumn(STATUS), rowNumber);
			if (status != null && !status.equals(ACCEPTED)) {
				return null;
			}

			// Convert accepted correlation
			MapCorrelation correlation = snap2snomedRelationshipTypeToCorrelationOrThrow(relationshipType, noMapFlag, rowNumber);
			if (correlation == MapCorrelation.TARGET_NOT_MAPPABLE) {
				targetCode = NO_SCT_MAP_TARGET_CONCEPT;
			}
			return new SheetRefsetMemberSimpleMapToSnomedWithCorrelation(sourceCode, targetCode, correlation);
		};
	}

	@Override
	protected Map<String, Function<RefsetMember, String>> getRefsetToSpreadsheetConversionMap() {
		Map<String, Function<RefsetMember, String>> simpleRefsetSheetColumns = new LinkedHashMap<>();
		// Source code	Source display	Target code	Target display	Relationship type code	Relationship type display	No map flag	Status
		simpleRefsetSheetColumns.put(SOURCE_CODE, member -> member.getAdditionalFields().get(MAP_SOURCE));
		simpleRefsetSheetColumns.put(SOURCE_DISPLAY, member -> "");// Not stored in Snowstorm
		simpleRefsetSheetColumns.put(TARGET_CODE, RefsetMember::getReferencedComponentId);
		simpleRefsetSheetColumns.put(TARGET_DISPLAY, RefsetMember::getReferencedComponentFsnOrBlank);
		simpleRefsetSheetColumns.put(RELATIONSHIP_TYPE_CODE, member -> {
			MapCorrelation correlation = MapCorrelation.fromConceptId(member.getAdditionalFields().get(CORRELATION_ID));
			return correlation != null ? correlation.name() : null;
		});
		return simpleRefsetSheetColumns;
	}

	@Override
	protected RefsetMember convertToMember(String refsetId, CodeSystemProperties config, SheetRefsetMember inputMember) {
		SheetRefsetMemberSimpleMapToSnomedWithCorrelation mapInputMember = (SheetRefsetMemberSimpleMapToSnomedWithCorrelation) inputMember;
		return new RefsetMember(refsetId, config.getDefaultModule(), inputMember.getReferenceComponentId())
				.setAdditionalField(MAP_SOURCE, mapInputMember.getSourceCode())
				.setAdditionalField(CORRELATION_ID, mapInputMember.getCorrelationIdOrNull());
	}

	@Override
	protected boolean matchMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		return Objects.equals(wantedRefsetMember.getAdditionalFields().get(MAP_SOURCE), storedMember.getAdditionalFields().get(MAP_SOURCE));
	}

	@Override
	protected boolean applyMember(RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		boolean changed = false;
		for (String s : Arrays.asList(MAP_SOURCE, CORRELATION_ID)) {
			if (applyField(s, wantedRefsetMember, storedMember)) {
				changed = true;
			}
		}
		if (!storedMember.isActive()) {
			storedMember.setActive(true);
			changed = true;
		}
		return changed;
	}

	private boolean applyField(String fieldName, RefsetMember wantedRefsetMember, RefsetMember storedMember) {
		String newValue = wantedRefsetMember.getAdditionalFields().get(fieldName);
		String oldValue = storedMember.getAdditionalFields().put(fieldName, newValue);
		return !Objects.equals(oldValue, newValue);
	}

	private MapCorrelation snap2snomedRelationshipTypeToCorrelationOrThrow(String relationshipType, String noMapFlag, Integer rowNumber) throws ServiceException {
		if ("1".equals(noMapFlag)) {
			return MapCorrelation.TARGET_NOT_MAPPABLE;
		}
		if (relationshipType == null) {
			throw throwMapCorrelationNotRecognised(null, rowNumber);
		}
		try {
			return MapCorrelation.valueOf(relationshipType);
		} catch (IllegalArgumentException e) {
			throw throwMapCorrelationNotRecognised(relationshipType, rowNumber);
		}
	}

	private ServiceException throwMapCorrelationNotRecognised(String relationshipType, Integer rowNumber) {
		return new ServiceException(String.format("Unrecognised Snap2SNOMED Relationship Type: '%s', on row: %s.", relationshipType, rowNumber));
	}

}
