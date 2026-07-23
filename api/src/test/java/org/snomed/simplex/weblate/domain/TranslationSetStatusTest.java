package org.snomed.simplex.weblate.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationSetStatusTest {

	@Test
	void isEditable_onlyReady() {
		assertThat(TranslationSetStatus.READY.isEditable()).isTrue();
		assertThat(TranslationSetStatus.PROCESSING.isEditable()).isFalse();
		assertThat(TranslationSetStatus.QUEUED_FOR_UPGRADE.isEditable()).isFalse();
		assertThat(TranslationSetStatus.UPGRADING.isEditable()).isFalse();
	}

	@Test
	void isBusy_includesProcessingStates() {
		assertThat(TranslationSetStatus.INITIALISING.isBusy()).isTrue();
		assertThat(TranslationSetStatus.PROCESSING.isBusy()).isTrue();
		assertThat(TranslationSetStatus.QUEUED_FOR_UPGRADE.isBusy()).isTrue();
		assertThat(TranslationSetStatus.UPGRADING.isBusy()).isTrue();
		assertThat(TranslationSetStatus.DELETING.isBusy()).isTrue();
		assertThat(TranslationSetStatus.READY.isBusy()).isFalse();
		assertThat(TranslationSetStatus.FAILED.isBusy()).isFalse();
	}
}
