package org.snomed.simplex.domain;

public class RefsetMemberIntentSimpleMapFromSnomed extends RefsetMemberIntent {

	private final String mapTarget;

	public RefsetMemberIntentSimpleMapFromSnomed(String sourceComponentId, String mapTarget) {
		super(sourceComponentId);
		this.mapTarget = mapTarget;
	}

	public String getMapTarget() {
		return mapTarget;
	}
}
