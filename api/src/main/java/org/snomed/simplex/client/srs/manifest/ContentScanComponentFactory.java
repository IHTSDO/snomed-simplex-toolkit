package org.snomed.simplex.client.srs.manifest;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;

import java.util.Set;

public class ContentScanComponentFactory extends ImpotentComponentFactory {

	private Set<Long> refsets;

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		super.newConceptState(conceptId, effectiveTime, active, moduleId, definitionStatusId);
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		super.newDescriptionState(id, effectiveTime, active, moduleId, conceptId, languageCode, typeId, term, caseSignificanceId);
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		super.newRelationshipState(id, effectiveTime, active, moduleId, sourceId, destinationId, relationshipGroup, typeId, characteristicTypeId, modifierId);
	}

	@Override
	public void newConcreteRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String value, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		super.newConcreteRelationshipState(id, effectiveTime, active, moduleId, sourceId, value, relationshipGroup, typeId, characteristicTypeId, modifierId);
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		super.newReferenceSetMemberState(fieldNames, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues);
	}
}
