package org.snomed.simplex.client.mlds.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MldsReleasePackageResponse(
		Long releasePackageId,
		List<MldsReleaseVersionResponse> releaseVersions
) {
}
