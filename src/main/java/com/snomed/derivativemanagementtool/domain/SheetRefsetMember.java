package com.snomed.derivativemanagementtool.domain;

public class SheetRefsetMember {

	private final String referenceComponentId;

	public SheetRefsetMember(String referenceComponentId) {
		this.referenceComponentId = referenceComponentId;
	}

	public String getReferenceComponentId() {
		return referenceComponentId;
	}
}
