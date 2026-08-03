package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationUnitOrderSyncTest {

	@Mock
	private SnolateTranslationSearchService translationSearchService;

	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;

	@Mock
	private SnolateTranslationUnitStore translationUnitStore;

	@Test
	void applyIfChanged_noOpWhenEqual() {
		TranslationUnit unit = unit("100", 5);
		TranslationSource source = new TranslationSource("100", "Asthma", 5);

		assertThat(TranslationUnitOrderSync.applyIfChanged(unit, source)).isFalse();
		assertThat(unit.getOrder()).isEqualTo(5);
	}

	@Test
	void applyIfChanged_updatesWhenDifferent() {
		TranslationUnit unit = unit("100", 0);
		TranslationSource source = new TranslationSource("100", "Asthma", 42);

		assertThat(TranslationUnitOrderSync.applyIfChanged(unit, source)).isTrue();
		assertThat(unit.getOrder()).isEqualTo(42);
	}

	@Test
	void syncBatch_returnsOnlyChangedUnits() {
		TranslationUnit unchanged = unit("100", 1);
		TranslationUnit changed = unit("200", 0);
		when(translationSourceRepository.findAllById(any())).thenReturn(List.of(
				new TranslationSource("100", "One", 1),
				new TranslationSource("200", "Two", 2)));

		List<TranslationUnit> updated = TranslationUnitOrderSync.syncBatch(List.of(unchanged, changed),
				translationSourceRepository);

		assertThat(updated).containsExactly(changed);
		assertThat(changed.getOrder()).isEqualTo(2);
	}

	@Test
	void syncAllUnits_savesChangedUnitsInBatches() {
		TranslationUnit stale = unit("100", 0);
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(0);
			consumer.accept(stale);
			return null;
		}).when(translationSearchService).forEachTranslationUnit(any());
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 99)));

		int updated = TranslationUnitOrderSync.syncAllUnits(translationSearchService, translationSourceRepository,
				translationUnitStore);

		assertThat(updated).isEqualTo(1);
		assertThat(stale.getOrder()).isEqualTo(99);
		verify(translationUnitStore).saveAll(List.of(stale));
	}

	@Test
	void syncAllUnits_skipsSaveWhenNothingChanged() {
		TranslationUnit current = unit("100", 7);
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(0);
			consumer.accept(current);
			return null;
		}).when(translationSearchService).forEachTranslationUnit(any());
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 7)));

		int updated = TranslationUnitOrderSync.syncAllUnits(translationSearchService, translationSourceRepository,
				translationUnitStore);

		assertThat(updated).isZero();
		verify(translationUnitStore, never()).saveAll(any());
	}

	private static TranslationUnit unit(String code, int order) {
		TranslationUnit unit = new TranslationUnit(code, "en-123", List.of("term"), TranslationStatus.APPROVED);
		unit.setOrder(order);
		return unit;
	}
}
