package org.snomed.simplex.snolate.domain;

/**
 * Workflow sort order for translation rows: lower first (matches legacy SQL CASE ordering).
 */
public final class TranslationStatuses {

	private TranslationStatuses() {
	}

	public static int sortOrdinal(TranslationStatus status) {
		if (status == null) {
			return 3;
		}
		return switch (status) {
			case NEEDS_EDIT -> 0;
			case FOR_REVIEW -> 1;
			case APPROVED -> 2;
			case NOT_STARTED -> 3;
		};
	}

	public static void applySortOrdinal(TranslationUnit unit) {
		if (unit != null) {
			unit.setStatusSort(sortOrdinal(unit.getStatus()));
		}
	}
}
