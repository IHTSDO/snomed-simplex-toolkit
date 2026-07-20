package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationToolServiceCsvImportTest {

	private static final String LANG = "es";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;

	private SnolateTranslationToolService service;
	private SnolateTranslationSet translationSet;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationToolService(translationUnitRepository, translationSourceRepository,
				translationSearchService);
		translationSet = new SnolateTranslationSet("SNOMEDCT-TEST", REFSET, "Test set", "test-set", "<< 138875005",
				TranslationSubsetType.SUB_TYPE, "SNOMEDCT-TEST");
		translationSet.setLanguageCode(LANG);
	}

	@Test
	void parseCsvLine_handlesQuotedCommasAndEscapes() {
		assertThat(SnolateTranslationToolService.parseCsvLine("a,b,c")).containsExactly("a", "b", "c");
		assertThat(SnolateTranslationToolService.parseCsvLine("\"a,b\",c")).containsExactly("a,b", "c");
		assertThat(SnolateTranslationToolService.parseCsvLine("\"say \"\"hi\"\"\",plain"))
				.containsExactly("say \"hi\"", "plain");
	}

	@Test
	void readCsvRow_handlesNewlinesInsideQuotedField() throws Exception {
		String csv = "Concept Code,Other Spanish Terms\n100,\"line1\nline2\"\n";
		StringReader reader = new StringReader(csv);
		assertThat(SnolateTranslationToolService.readCsvRow(reader))
				.containsExactly("Concept Code", "Other Spanish Terms");
		assertThat(SnolateTranslationToolService.readCsvRow(reader))
				.containsExactly("100", "line1\nline2");
		assertThat(SnolateTranslationToolService.readCsvRow(reader)).isEmpty();
	}

	@Test
	void importTranslationSetCsv_importsTranslationStudioExportFormat() throws Exception {
		TranslationUnit unit = unit("100", List.of("old"), TranslationStatus.NOT_STARTED);
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("100", COMPOSITE))
				.thenReturn(Optional.of(unit));

		String csv = """
				Concept Code,English Term,Spanish Preferred Term,Other Spanish Terms,Status,URL
				100,Asthma,asma,asma crónica,Ready for review,https://snomed.info/id/100
				""";
		ChangeSummary summary = service.importTranslationSetCsv(
				translationSet,
				new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
				"Concept Code",
				List.of("Spanish Preferred Term", "Other Spanish Terms"),
				TranslationStatus.FOR_REVIEW);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma", "asma crónica");
		assertThat(unit.getStatus()).isEqualTo(TranslationStatus.FOR_REVIEW);
		verify(translationUnitRepository).save(unit);
	}

	@Test
	void importTranslationSetCsv_importsLegacyContextTargetFormat() throws Exception {
		TranslationUnit unit = unit("200", List.of(), TranslationStatus.NOT_STARTED);
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("200", COMPOSITE))
				.thenReturn(Optional.of(unit));

		String csv = """
				context,target
				200,asma
				""";
		ChangeSummary summary = service.importTranslationSetCsv(
				translationSet,
				new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
				"context",
				List.of("target"),
				TranslationStatus.APPROVED);

		assertThat(summary.getUpdated()).isEqualTo(1);
		assertThat(unit.getTerms()).containsExactly("asma");
		assertThat(unit.getStatus()).isEqualTo(TranslationStatus.APPROVED);
	}

	@Test
	void importTranslationSetCsv_skipsUnknownConceptCodes() throws Exception {
		when(translationUnitRepository.findByCodeAndCompositeLanguageCode("999", COMPOSITE))
				.thenReturn(Optional.empty());

		String csv = """
				context,target
				999,unknown term
				""";
		ChangeSummary summary = service.importTranslationSetCsv(
				translationSet,
				new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
				"context",
				List.of("target"),
				TranslationStatus.FOR_REVIEW);

		assertThat(summary.getUpdated()).isZero();
	}

	@Test
	void importTranslationSetCsv_rejectsInvalidStatus() {
		String csv = "context,target\n100,term\n";
		assertThatThrownBy(() -> service.importTranslationSetCsv(
				translationSet,
				new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
				"context",
				List.of("target"),
				TranslationStatus.NOT_STARTED))
				.isInstanceOf(ServiceExceptionWithStatusCode.class)
				.hasMessageContaining("NEEDS_EDIT, FOR_REVIEW, or APPROVED");
	}

	@Test
	void importTranslationSetCsv_rejectsMissingMappedColumn() {
		String csv = "context,target\n100,term\n";
		assertThatThrownBy(() -> service.importTranslationSetCsv(
				translationSet,
				new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
				"missing",
				List.of("target"),
				TranslationStatus.FOR_REVIEW))
				.isInstanceOf(ServiceExceptionWithStatusCode.class)
				.hasMessageContaining("Concept column");
	}

	private TranslationUnit unit(String code, List<String> terms, TranslationStatus status) {
		return new TranslationUnit(
				new TranslationUnit.MembershipKey(code, REFSET, LANG, COMPOSITE, 0),
				terms,
				status,
				new LinkedHashSet<>(Set.of(translationSet.getCompositeSetCode())));
	}
}
