package org.snomed.simplex.snolate.domain;

/**
 * Workflow sort order for translation rows: lower first (matches legacy SQL CASE ordering).
 */
public final class TranslationStatuses {

	private TranslationStatuses() {
	}

	public static int sortOrdinal(TranslationStatus status) {
		if (status == null) {
			return 4;
		}
		return switch (status) {
			case NEEDS_EDIT -> 0;
			case FOR_REVIEW -> 1;
			case APPROVED -> 2;
			case COMPLETE -> 3;
			case NOT_STARTED -> 4;
		};
	}

	public static void applySortOrdinal(TranslationUnit unit) {
		if (unit != null) {
			unit.setStatusSort(sortOrdinal(unit.getStatus()));
		}
	}

	/** Whether a unit may be used as an inline batch-translation context example. */
	public static boolean isAcceptedContext(TranslationUnit unit) {
		if (unit == null || !unit.hasTermContent()) {
			return false;
		}
		TranslationStatus status = unit.getStatus();
		return status == TranslationStatus.APPROVED || status == TranslationStatus.COMPLETE;
	}
}
