package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationSourceType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationSourceTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateTranslationUnitStore translationUnitStore;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;

	private SnolateTranslationSource source;

	@BeforeEach
	void setUp() {
		source = new SnolateTranslationSource(translationUnitStore, translationSearchService,
				translationSourceRepository, LANG, REFSET);
	}

	@Test
	void readTranslation_mapsPersistedUnits() throws Exception {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(new TranslationUnit("100", COMPOSITE, List.of("alpha", "beta"), TranslationStatus.FOR_REVIEW));
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsEntry(100L, List.of("alpha", "beta"));
		assertThat(source.getType()).isEqualTo(TranslationSourceType.SNOLATE);
	}

	@Test
	void readTranslation_rejectsNonNumericCode() {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(new TranslationUnit("x", COMPOSITE, List.of("t"), TranslationStatus.APPROVED));
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		assertThatThrownBy(source::readTranslation)
				.hasMessageContaining("non-numeric code");
	}

	@Test
	void writeTranslation_createsAndMergesAdditions() throws Exception {
		TranslationUnit existing = new TranslationUnit(
				new TranslationUnit.MembershipKey("200", REFSET, LANG, COMPOSITE, 0), List.of("existing"), TranslationStatus.NEEDS_EDIT, Set.of());
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any()))
				.thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					Iterable<String> codes = invocation.getArgument(1);
					Map<String, TranslationUnit> found = new HashMap<>();
					for (String code : codes) {
						if ("200".equals(code)) {
							found.put("200", existing);
						}
					}
					return found;
				});
		when(translationSourceRepository.findAllById(any())).thenReturn(List.of());

		TranslationState additions = new TranslationState();
		additions.getConceptTerms().put(200L, List.of("extra"));
		additions.getConceptTerms().put(201L, List.of("only"));
		source.writeTranslation(additions);

		assertThat(existing.getTerms()).containsExactly("existing", "extra");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore, times(1)).saveAll(captor.capture());
		List<TranslationUnit> saved = new ArrayList<>(captor.getValue());
		TranslationUnit created = saved.stream().filter(u -> "201".equals(u.getCode())).findFirst().orElseThrow();
		assertThat(created.getTerms()).containsExactly("only");
		assertThat(created.getStatus()).isEqualTo(TranslationStatus.APPROVED);
		assertThat(saved).hasSize(2);
	}

	@Test
	void writeTranslation_newUnitUsesTranslationSourceOrder() throws Exception {
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any())).thenReturn(Map.of());
		when(translationSourceRepository.findAllById(List.of("201")))
				.thenReturn(List.of(new TranslationSource("201", "Only", 42)));

		TranslationState additions = new TranslationState();
		additions.getConceptTerms().put(201L, List.of("only"));
		source.writeTranslation(additions);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<TranslationUnit>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(translationUnitStore).saveAll(captor.capture());
		TranslationUnit created = captor.getValue().iterator().next();
		assertThat(created.getOrder()).isEqualTo(42);
	}

	@Test
	void writeTranslation_existingUnitOrderUnchanged() throws Exception {
		TranslationUnit existing = new TranslationUnit(
				new TranslationUnit.MembershipKey("200", REFSET, LANG, COMPOSITE, 7), List.of("existing"),
				TranslationStatus.NEEDS_EDIT, Set.of());
		when(translationUnitStore.loadByCodes(eq(COMPOSITE), any())).thenReturn(Map.of("200", existing));
		when(translationSourceRepository.findAllById(List.of("200")))
				.thenReturn(List.of(new TranslationSource("200", "Existing", 99)));

		TranslationState additions = new TranslationState();
		additions.getConceptTerms().put(200L, List.of("extra"));
		source.writeTranslation(additions);

		assertThat(existing.getOrder()).isEqualTo(7);
	}

	@Test
	void readTranslation_ignoresOtherLanguageBuckets() throws Exception {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(1);
			consumer.accept(new TranslationUnit("100", COMPOSITE, List.of("en-term"), TranslationStatus.APPROVED));
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

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
