package com.snomed.simplextoolkit.domain;

public class RefsetMemberIntent {

	private final String referenceComponentId;

	public RefsetMemberIntent(String referenceComponentId) {
		this.referenceComponentId = referenceComponentId;
	}

	public String getReferenceComponentId() {
		return referenceComponentId;
	}
}
