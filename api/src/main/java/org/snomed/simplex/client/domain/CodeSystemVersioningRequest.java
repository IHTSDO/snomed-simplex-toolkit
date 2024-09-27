package org.snomed.simplex.client.domain;

public class CodeSystemVersioningRequest {

	private String effectiveDate;
	private String description;
	private boolean internalRelease;

	public CodeSystemVersioningRequest(String effectiveDate, String description, boolean internalRelease) {
		this.effectiveDate = effectiveDate;
		this.description = description;
		this.internalRelease = internalRelease;
	}

	public String getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(String effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isInternalRelease() {
		return internalRelease;
	}

	public void setInternalRelease(boolean internalRelease) {
		this.internalRelease = internalRelease;
	}
}
