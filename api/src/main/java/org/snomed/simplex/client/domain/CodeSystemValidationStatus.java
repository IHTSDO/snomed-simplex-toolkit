package org.snomed.simplex.client.domain;

public enum CodeSystemValidationStatus {

	TODO(false), IN_PROGRESS(false), CONTENT_ERROR(true), CONTENT_WARNING(true),
	SYSTEM_ERROR(false), COMPLETE(true), STALE(false);

	private final boolean canTurnStale;

	CodeSystemValidationStatus(boolean canTurnStale) {
		this.canTurnStale = canTurnStale;
	}

	public boolean isCanTurnStale() {
		return canTurnStale;
	}
}
