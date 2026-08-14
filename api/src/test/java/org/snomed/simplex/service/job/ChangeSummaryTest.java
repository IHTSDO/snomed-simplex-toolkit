package org.snomed.simplex.service.job;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeSummaryTest {

	@Test
	void recordSkippedNotFound_recordsUniqueCodesUpToLimit() {
		ChangeSummary summary = new ChangeSummary();

		summary.recordSkippedNotFound("100");
		summary.recordSkippedNotFound("100");
		summary.recordSkippedNotFound("200");

		assertThat(summary.getSkippedNotFound()).isEqualTo(3);
		assertThat(summary.getSkippedNotFoundCodes()).containsExactly("100", "200");
	}

	@Test
	void recordSkippedNotFound_capsStoredCodesAt500() {
		ChangeSummary summary = new ChangeSummary();

		IntStream.rangeClosed(1, 502).forEach(i -> summary.recordSkippedNotFound(Integer.toString(i)));

		assertThat(summary.getSkippedNotFound()).isEqualTo(502);
		assertThat(summary.getSkippedNotFoundCodes()).hasSize(ChangeSummary.MAX_SKIPPED_NOT_FOUND_CODES);
		assertThat(summary.getSkippedNotFoundCodes().get(0)).isEqualTo("1");
		assertThat(summary.getSkippedNotFoundCodes().get(499)).isEqualTo("500");
	}
}
