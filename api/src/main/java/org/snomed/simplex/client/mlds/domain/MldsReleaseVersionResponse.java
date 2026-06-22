package org.snomed.simplex.client.mlds.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MldsReleaseVersionResponse(
		Long releaseVersionId,
		String versionURI
) {
}
