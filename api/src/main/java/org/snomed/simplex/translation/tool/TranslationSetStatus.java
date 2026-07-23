package org.snomed.simplex.translation.tool;

public enum TranslationSetStatus {

	INITIALISING,
	PROCESSING,
	QUEUED_FOR_UPGRADE,
	UPGRADING,
	READY,
	FAILED,
	DELETING;

	public boolean isEditable() {
		return this == READY;
	}

	public boolean isBusy() {
		return this == INITIALISING || this == PROCESSING || this == QUEUED_FOR_UPGRADE || this == UPGRADING || this == DELETING;
	}

}
