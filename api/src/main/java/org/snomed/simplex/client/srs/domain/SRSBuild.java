package org.snomed.simplex.client.srs.domain;

import java.util.Collections;
import java.util.List;

public record SRSBuild(String id, String url, String creationTime, String status, List<String> tags, SRSBuildConfiguration configuration) {

	public List<String> getTags() {
		return tags != null ? tags : Collections.emptyList();
	}

}
