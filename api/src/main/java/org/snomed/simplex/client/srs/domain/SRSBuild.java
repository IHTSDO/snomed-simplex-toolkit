package org.snomed.simplex.client.srs.domain;

import static org.snomed.simplex.client.srs.domain.SRSSimpleBuildStatus.COMPLETED;
import static org.snomed.simplex.client.srs.domain.SRSSimpleBuildStatus.TODO;

public record SRSBuild(String id, String url, String creationTime, String status) {

	public SRSSimpleBuildStatus getSimpleStatus() {
		if (status != null) {
			if (status.startsWith("RELEASE_COMPLETE")) {
				return COMPLETED;
			}
			if (status.startsWith("RELEASE_COMPLETE")) {
				return COMPLETED;
			}
		}
		return TODO;
	}

}
