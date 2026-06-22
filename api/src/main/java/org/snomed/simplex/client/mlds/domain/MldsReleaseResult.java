package org.snomed.simplex.client.mlds.domain;

public record MldsReleaseResult(
		long releasePackageId,
		long releaseVersionId,
		long releaseFileId,
		String versionURI
) {
}
