package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationStatusLabels;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationServiceCsvExportTest {

	private static final String LANG = "es";
	private static final String REFSET = "1000123";

	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private SnolateTranslationUnitStore translationUnitStore;

	private SnolateTranslationService service;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationService(translationSourceRepository,
				translationSearchService, translationUnitStore);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void displayLanguageDialect_stripsLanguageRefsetSuffix() {
		assertThat(SnolateTranslationService.displayLanguageDialect("Spanish language reference set"))
				.isEqualTo("Spanish");
		assertThat(SnolateTranslationService.displayLanguageDialect("French language refset"))
				.isEqualTo("French");
		assertThat(SnolateTranslationService.displayLanguageDialect("  "))
				.isEqualTo("Translation");
	}

	@Test
	void escapeCsvField_quotesFieldsWithSpecialCharacters() {
		assertThat(SnolateTranslationService.escapeCsvField("plain")).isEqualTo("plain");
		assertThat(SnolateTranslationService.escapeCsvField("a,b")).isEqualTo("a,b");
		assertThat(SnolateTranslationService.escapeCsvField("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(SnolateTranslationService.escapeCsvField("line1\nline2")).isEqualTo("\"line1\nline2\"");
		assertThat(SnolateTranslationService.escapeCsvField("a\tb")).isEqualTo("\"a\tb\"");
	}

	@Test
	void writeTranslationSetCsv_writesHeaderAndMappedRows() throws Exception {
		TranslationUnit unit = unit("100", List.of("asma", "asma crónica"), TranslationStatus.FOR_REVIEW);
		stubUnitStream(List.of(unit));
		when(translationSourceRepository.findAllById(List.of("100")))
				.thenReturn(List.of(new TranslationSource("100", "Asthma", 0)));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		service.writeTranslationSetCsv(translationSet, TranslationStatus.FOR_REVIEW, "Spanish", out);

		String csv = out.toString(StandardCharsets.UTF_8);
		assertThat(csv)
				.startsWith("Concept Code\tEnglish Term\tSpanish Preferred Term\tOther Spanish Terms\tStatus\tURL\n")
				.contains("100\tAsthma\tasma\tasma crónica\tReady for review\thttps://snomed.info/id/100\n");
	}

	@Test
	void writeTranslationSetCsv_passesStatusFilterToStream() throws Exception {
		stubUnitStream(List.of());

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		service.writeTranslationSetCsv(translationSet, TranslationStatus.APPROVED, "Spanish", out);

		ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
		verify(translationSearchService).forEachUnitInSet(eq(translationSet.getCompositeSetCode()),
				eq(translationSet.getLanguageCodeWithRefsetId()), eq(TranslationStatus.APPROVED),
				sortCaptor.capture(), any());
		assertThat(sortCaptor.getValue()).isEqualTo(SnolateTranslationSearchService.UNITS_IN_SET_EXPORT_SORT);
		verify(translationSearchService, never()).pageUnitsInSet(any(), any(), any(), any(), any(), any());
	}

	@Test
	void writeTranslationSetCsv_batchesSourceLookupsInChunks() throws Exception {
		List<TranslationUnit> units = new ArrayList<>();
		for (int i = 0; i < 2_500; i++) {
			units.add(unit(String.valueOf(i), List.of("term-" + i), TranslationStatus.APPROVED));
		}
		stubUnitStream(units);
		when(translationSourceRepository.findAllById(any())).thenAnswer(invocation -> {
			List<String> codes = invocation.getArgument(0);
			return codes.stream().map(code -> new TranslationSource(code, "English-" + code, 0)).toList();
		});

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		service.writeTranslationSetCsv(translationSet, TranslationStatus.APPROVED, "Spanish", out);

		ArgumentCaptor<List<String>> codesCaptor = ArgumentCaptor.forClass(List.class);
		verify(translationSourceRepository, times(3)).findAllById(codesCaptor.capture());
		assertThat(codesCaptor.getAllValues().get(0)).hasSize(1_000);
		assertThat(codesCaptor.getAllValues().get(1)).hasSize(1_000);
		assertThat(codesCaptor.getAllValues().get(2)).hasSize(500);
		assertThat(out.toString(StandardCharsets.UTF_8).lines().count()).isEqualTo(2_501L);
	}

	@Test
	void translationStatusLabels_matchUiRadioLabels() {
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.FOR_REVIEW)).isEqualTo("Ready for review");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.NOT_STARTED)).isEqualTo("Not started");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.NEEDS_EDIT)).isEqualTo("Needs editing");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.APPROVED)).isEqualTo("Ready to push");
		assertThat(TranslationStatusLabels.radioLabel(TranslationStatus.COMPLETE)).isEqualTo("Pushed");
		assertThat(TranslationStatusLabels.exportFilenameSlug(TranslationStatus.FOR_REVIEW)).isEqualTo("ready-for-review");
		assertThat(TranslationStatusLabels.exportFilenameSlug(null)).isEqualTo("all-concepts");
	}

	private void stubUnitStream(List<TranslationUnit> units) {
		doAnswer(invocation -> {
			Consumer<TranslationUnit> consumer = invocation.getArgument(4);
			for (TranslationUnit unit : units) {
				consumer.accept(unit);
			}
			return null;
		}).when(translationSearchService).forEachUnitInSet(any(), any(), any(), any(Sort.class), any());
	}

	private static TranslationUnit unit(String code, List<String> terms, TranslationStatus status) {
		return new TranslationUnit(code, LANG + "-" + REFSET, terms, status);
	}
}
