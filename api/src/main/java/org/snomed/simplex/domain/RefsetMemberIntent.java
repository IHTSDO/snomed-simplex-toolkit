package org.snomed.simplex.domain;

public class RefsetMemberIntent implements ComponentIntent {

	private final String referenceComponentId;

	public RefsetMemberIntent(String referenceComponentId) {
		this.referenceComponentId = referenceComponentId;
	}

	public String getReferenceComponentId() {
		return referenceComponentId;
	}
}
