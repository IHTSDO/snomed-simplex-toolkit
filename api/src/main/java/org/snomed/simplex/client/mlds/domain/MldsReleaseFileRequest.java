package org.snomed.simplex.client.mlds.domain;

public record MldsReleaseFileRequest(
		String label,
		String downloadUrl,
		String md5Hash,
		long fileSize,
		boolean primaryFile
) {
}
