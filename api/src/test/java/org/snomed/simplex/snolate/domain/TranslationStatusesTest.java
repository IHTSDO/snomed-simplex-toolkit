package org.snomed.simplex.snolate.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationStatusesTest {

	@Test
	void sortOrdinal_ordersCompleteAfterApprovedBeforeNotStarted() {
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.NEEDS_EDIT)).isEqualTo(0);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.FOR_REVIEW)).isEqualTo(1);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.APPROVED)).isEqualTo(2);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.COMPLETE)).isEqualTo(3);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.NOT_STARTED)).isEqualTo(4);
		assertThat(TranslationStatuses.sortOrdinal(null)).isEqualTo(4);
	}
}
