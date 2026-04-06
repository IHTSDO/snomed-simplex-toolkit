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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
		when(translationUnitRepository.findAllByCompositeLanguageCodeAndCodeIn(eq(COMPOSITE), any()))
				.thenAnswer(invocation -> {
					Collection<String> codes = invocation.getArgument(1);
					List<TranslationUnit> found = new ArrayList<>();
					if (codes.contains("200")) {
						found.add(existing);
					}
					return found;
				});

		TranslationState additions = new TranslationState();
		additions.getConceptTerms().put(200L, List.of("extra"));
		additions.getConceptTerms().put(201L, List.of("only"));
		source.writeTranslation(additions);

		assertThat(existing.getTerms()).containsExactly("existing", "extra");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<TranslationUnit>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository, times(1)).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>();
		captor.getValue().forEach(saved::add);
		TranslationUnit created = saved.stream().filter(u -> "201".equals(u.getCode())).findFirst().orElseThrow();
		assertThat(created.getTerms()).containsExactly("only");
		assertThat(created.getStatus()).isEqualTo(TranslationStatus.APPROVED);
		assertThat(saved).hasSize(2);
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
