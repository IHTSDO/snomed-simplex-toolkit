package org.snomed.simplex.client.mlds.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MldsReleaseFileResponse(
		Long releaseFileId
) {
}
