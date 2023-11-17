package com.snomed.simplextoolkit.client.domain;

public class SnowstormClassificationJob {

	public enum Status {
		SCHEDULED, RUNNING, FAILED, COMPLETED, STALE, SAVING_IN_PROGRESS, SAVED, SAVE_FAILED
	}

	private Status status;
	private boolean equivalentConceptsFound;

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public boolean isEquivalentConceptsFound() {
		return equivalentConceptsFound;
	}

	public void setEquivalentConceptsFound(boolean equivalentConceptsFound) {
		this.equivalentConceptsFound = equivalentConceptsFound;
	}
}
