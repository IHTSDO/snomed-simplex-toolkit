package org.snomed.simplex.client.domain;

import org.snomed.simplex.domain.JobStatus;

public enum CodeSystemBuildStatus {

	TODO, IN_PROGRESS, FAILED, COMPLETE;

	public static CodeSystemBuildStatus fromSRStatus(String status) {
		if (status != null) {
			if (status.contains("CANCELLED") || status.contains("FAILED")) {
				return FAILED;
			} else if (status.startsWith("RELEASE_COMPLETE")) {
				return COMPLETE;
			}
			return IN_PROGRESS;
		}
		return TODO;
	}

	public static CodeSystemBuildStatus fromBranchMetadata(String status) {
		if (status != null) {
			return CodeSystemBuildStatus.valueOf(status);
		}
		return TODO;
	}
}
