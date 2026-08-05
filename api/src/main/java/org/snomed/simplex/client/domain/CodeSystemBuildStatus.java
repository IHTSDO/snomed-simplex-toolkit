package org.snomed.simplex.client.domain;

import java.util.Set;

public enum CodeSystemBuildStatus {

	TODO, IN_PROGRESS, FAILED, COMPLETE;

	private static final Set<String> IN_PROGRESS_SRS_STATUSES = Set.of(
			"PENDING",
			"QUEUED",
			"BEFORE_TRIGGER",
			"BUILDING",
			"BUILT",
			"RVF_QUEUED",
			"RVF_RUNNING",
			"CANCEL_REQUESTED",
			"UNKNOWN"
	);

	public static CodeSystemBuildStatus fromSRSStatus(String status) {
		if (status == null || status.isBlank()) {
			return TODO;
		}
		String normalized = status.toUpperCase();
		if (normalized.contains("CANCELLED") || normalized.contains("FAILED")) {
			return FAILED;
		}
		if (normalized.startsWith("RELEASE_COMPLETE")) {
			return COMPLETE;
		}
		if (IN_PROGRESS_SRS_STATUSES.contains(normalized)) {
			return IN_PROGRESS;
		}
		return FAILED;
	}

	public static CodeSystemBuildStatus fromBranchMetadata(String status) {
		if (status != null) {
			return CodeSystemBuildStatus.valueOf(status);
		}
		return TODO;
	}

	public boolean isTerminal() {
		return this == FAILED || this == COMPLETE;
	}
}
