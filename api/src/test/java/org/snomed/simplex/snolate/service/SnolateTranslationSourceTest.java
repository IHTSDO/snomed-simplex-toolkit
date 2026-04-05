package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationSourceType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationSourceTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;

	private SnolateTranslationSource source;

	@BeforeEach
	void setUp() {
		source = new SnolateTranslationSource(translationUnitRepository, LANG, REFSET);
	}

	@Test
	void readTranslation_mapsPersistedUnits() throws Exception {
		when(translationUnitRepository.findAllByCompositeLanguageCode(COMPOSITE))
				.thenReturn(List.of(new TranslationUnit("100", COMPOSITE, List.of("alpha", "beta"), TranslationStatus.FOR_REVIEW)));

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsEntry(100L, List.of("alpha", "beta"));
		assertThat(source.getType()).isEqualTo(TranslationSourceType.SNOLATE);
	}

	@Test
	void readTranslation_rejectsNonNumericCode() {
		when(translationUnitRepository.findAllByCompositeLanguageCode(COMPOSITE))
				.thenReturn(List.of(new TranslationUnit("x", COMPOSITE, List.of("t"), TranslationStatus.APPROVED)));

		assertThatThrownBy(source::readTranslation)
				.hasMessageContaining("non-numeric code");
	}

	@Test
	void writeTranslation_createsAndMergesAdditions() throws Exception {
		TranslationUnit existing = new TranslationUnit("200", REFSET, LANG, COMPOSITE, 0, List.of("existing"), TranslationStatus.NEEDS_EDIT, Set.of());
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("200", COMPOSITE)).thenReturn(Optional.of(existing));
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("201", COMPOSITE)).thenReturn(Optional.empty());
		when(translationUnitRepository.save(any(TranslationUnit.class))).thenAnswer(inv -> inv.getArgument(0));

		TranslationState additions = new TranslationState();
		additions.getConceptTerms().put(200L, List.of("extra"));
		additions.getConceptTerms().put(201L, List.of("only"));
		source.writeTranslation(additions);

		assertThat(existing.getTerms()).containsExactly("existing", "extra");
		ArgumentCaptor<TranslationUnit> captor = ArgumentCaptor.forClass(TranslationUnit.class);
		verify(translationUnitRepository, times(2)).save(captor.capture());
		TranslationUnit created = captor.getAllValues().stream().filter(u -> "201".equals(u.getCode())).findFirst().orElseThrow();
		assertThat(created.getTerms()).containsExactly("only");
		assertThat(created.getStatus()).isEqualTo(TranslationStatus.APPROVED);
	}

	@Test
	void readTranslation_ignoresOtherLanguageBuckets() throws Exception {
		when(translationUnitRepository.findAllByCompositeLanguageCode(COMPOSITE))
				.thenReturn(List.of(new TranslationUnit("100", COMPOSITE, List.of("en-term"), TranslationStatus.APPROVED)));

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsOnlyKeys(100L);
		assertThat(state.getConceptTerms().get(100L)).containsExactly("en-term");
	}

	@Test
	void mergeAdditions_prependsWhenUnitWasEmpty() {
		assertThat(SnolateTranslationSource.mergeAdditions(List.of(), List.of("b", "a")))
				.containsExactly("b", "a");
	}
}
