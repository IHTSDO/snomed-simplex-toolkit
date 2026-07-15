package org.snomed.simplex.snolate.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationStatusesTest {

	@Test
	void sortOrdinal_ordersCompleteAfterApprovedBeforeNotStarted() {
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.NEEDS_EDIT)).isZero();
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.FOR_REVIEW)).isEqualTo(1);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.APPROVED)).isEqualTo(2);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.COMPLETE)).isEqualTo(3);
		assertThat(TranslationStatuses.sortOrdinal(TranslationStatus.NOT_STARTED)).isEqualTo(4);
		assertThat(TranslationStatuses.sortOrdinal(null)).isEqualTo(4);
	}

	@Test
	void isAcceptedContext_requiresApprovedOrCompleteWithTerms() {
		TranslationUnit approved = new TranslationUnit("100", "es-1", List.of("Asma"), TranslationStatus.APPROVED);
		TranslationUnit complete = new TranslationUnit("101", "es-1", List.of("Diabetes"), TranslationStatus.COMPLETE);
		TranslationUnit forReview = new TranslationUnit("102", "es-1", List.of("Provisional"), TranslationStatus.FOR_REVIEW);
		TranslationUnit empty = new TranslationUnit("103", "es-1", List.of(), TranslationStatus.APPROVED);

		assertThat(TranslationStatuses.isAcceptedContext(approved)).isTrue();
		assertThat(TranslationStatuses.isAcceptedContext(complete)).isTrue();
		assertThat(TranslationStatuses.isAcceptedContext(forReview)).isFalse();
		assertThat(TranslationStatuses.isAcceptedContext(empty)).isFalse();
		assertThat(TranslationStatuses.isAcceptedContext(null)).isFalse();
	}
}
