package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationUnitStoreTest {

	private static final String COMPOSITE = "nl-31000172101";

	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;

	private SnolateTranslationUnitStore store;

	@BeforeEach
	void setUp() {
		store = new SnolateTranslationUnitStore(translationUnitRepository);
	}

	@Test
	void loadByCodes_usesCanonicalIds() {
		TranslationUnit unit = unit("100");
		when(translationUnitRepository.findAllById(List.of("nl-31000172101_100", "nl-31000172101_200")))
				.thenReturn(List.of(unit));

		Map<String, TranslationUnit> loaded = store.loadByCodes(COMPOSITE, List.of("100", "200"));

		assertThat(loaded).containsEntry("100", unit);
		assertThat(loaded).doesNotContainKey("200");
	}

	@Test
	void loadByCode_returnsPresentUnit() {
		TranslationUnit unit = unit("100");
		when(translationUnitRepository.findAllById(List.of("nl-31000172101_100"))).thenReturn(List.of(unit));

		Optional<TranslationUnit> loaded = store.loadByCode(COMPOSITE, "100");

		assertThat(loaded).contains(unit);
	}

	@Test
	void saveAll_assignsCanonicalIdBeforePersist() {
		TranslationUnit unit = unit("100");
		unit.setId(null);

		store.saveAll(List.of(unit));

		ArgumentCaptor<Iterable<TranslationUnit>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).saveAll(captor.capture());
		TranslationUnit saved = captor.getValue().iterator().next();
		assertThat(saved.getId()).isEqualTo("nl-31000172101_100");
	}

	private static TranslationUnit unit(String code) {
		return new TranslationUnit(code, COMPOSITE, List.of("term"), TranslationStatus.APPROVED);
	}
}
