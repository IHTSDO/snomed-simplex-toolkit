package org.snomed.simplex.snolate.domain;

public enum TranslationStatus {
	NEEDS_EDIT, FOR_REVIEW, APPROVED,
	/** Shell row: in a Snolate set but no translation work started yet */
	NOT_STARTED
}
