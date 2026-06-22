package org.snomed.simplex.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.mlds.domain.MldsReleaseVersionResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MldsReleaseServiceTest {

	@Test
	void formatMonthYear() {
		assertEquals("June 2026", MldsReleaseService.formatMonthYear(20260603));
		assertEquals("December 2025", MldsReleaseService.formatMonthYear(20251215));
	}

	@Test
	void formatPublishedDate() {
		assertEquals("2026-06-03", MldsReleaseService.formatPublishedDate(20260603));
	}

	@Test
	void contentItemIdentifierAndVersionUri() {
		assertEquals("http://snomed.info/sct/11000279109", MldsReleaseService.contentItemIdentifier("11000279109"));
		assertEquals("http://snomed.info/sct/11000279109/version/20260603", MldsReleaseService.versionUri("11000279109", "20260603"));
	}

	@Test
	void applyTemplate() {
		String template = "https://example.com/SIMPLEX_{moduleId}/{effectiveTime}/{packageName}";
		String result = MldsReleaseService.applyTemplate(template, "11000279109", "20260603", "release.zip");
		assertEquals("https://example.com/SIMPLEX_11000279109/20260603/release.zip", result);
	}

	@Test
	void hasDuplicateVersionUri() {
		List<MldsReleaseVersionResponse> versions = List.of(
				new MldsReleaseVersionResponse(1L, "http://snomed.info/sct/11000279109/version/20260603"),
				new MldsReleaseVersionResponse(2L, "http://snomed.info/sct/11000279109/version/20251215")
		);
		assertTrue(MldsReleaseService.hasDuplicateVersionUri(versions, "http://snomed.info/sct/11000279109/version/20260603"));
		assertFalse(MldsReleaseService.hasDuplicateVersionUri(versions, "http://snomed.info/sct/11000279109/version/20260101"));
		assertFalse(MldsReleaseService.hasDuplicateVersionUri(null, "http://snomed.info/sct/11000279109/version/20260603"));
	}
}
