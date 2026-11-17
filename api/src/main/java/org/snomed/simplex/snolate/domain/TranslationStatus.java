package org.snomed.simplex.snolate.domain;

public enum TranslationStatus {
	NEEDS_EDIT, FOR_REVIEW, APPROVED,
	/** AUTHORING_PLATFORM mode only: pushed to a Managed Service translation task branch, awaiting promotion or rejection */
	PROMOTED_TO_TASK,
	/** Set automatically when unit content is in sync with Snowstorm */
	COMPLETE,
	/** Shell row: in a Snolate set but no translation work started yet */
	NOT_STARTED
}
