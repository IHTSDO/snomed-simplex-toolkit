package org.snomed.simplex.snolate.domain;

/**
 * Human-readable labels for {@link TranslationStatus}, aligned with the Translation Studio UI.
 */
public final class TranslationStatusLabels {

	private TranslationStatusLabels() {
	}

	public static String radioLabel(TranslationStatus status) {
		if (status == null) {
			return "Not started";
		}
		return switch (status) {
			case NEEDS_EDIT -> "Needs editing";
			case FOR_REVIEW -> "Ready for review";
			case APPROVED -> "Ready to push";
			case COMPLETE -> "Pushed";
			case NOT_STARTED -> "Not started";
		};
	}

	public static String radioLabel(String statusName) {
		if (statusName == null || statusName.isBlank()) {
			return "Not started";
		}
		try {
			return radioLabel(TranslationStatus.valueOf(statusName.trim()));
		} catch (IllegalArgumentException e) {
			return statusName;
		}
	}

	public static String exportFilenameSlug(TranslationStatus statusFilter) {
		if (statusFilter == null) {
			return "all-concepts";
		}
		return switch (statusFilter) {
			case NOT_STARTED -> "not-started";
			case NEEDS_EDIT -> "needs-editing";
			case FOR_REVIEW -> "ready-for-review";
			case APPROVED -> "ready-to-push";
			case COMPLETE -> "pushed";
		};
	}
}
