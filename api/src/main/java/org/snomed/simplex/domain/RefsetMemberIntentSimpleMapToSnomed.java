package org.snomed.simplex.domain;

public class RefsetMemberIntentSimpleMapToSnomed extends RefsetMemberIntent {

	private final String mapSource;

	public RefsetMemberIntentSimpleMapToSnomed(String mapSource, String targetComponentId) {
		super(targetComponentId);
		this.mapSource = mapSource;
	}

	public String getMapSource() {
		return mapSource;
	}
}
