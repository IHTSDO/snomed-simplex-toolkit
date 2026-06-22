package org.snomed.simplex.client.mlds.domain;

public record MldsReleaseVersionRequest(
		String name,
		String description,
		String summary,
		String releaseType,
		String packageType,
		String versionURI,
		String versionDependentURI,
		String versionDependentDerivativeURI,
		String publishedAt
) {
}
