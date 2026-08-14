package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportTermsMergerTest {

	@Test
	void mergeTerms_firstRowOnly() {
		assertThat(ImportTermsMerger.mergeTerms(null, List.of("asma")))
				.containsExactly("asma");
	}

	@Test
	void mergeTerms_twoRows_firstRowIsPt() {
		List<String> first = ImportTermsMerger.mergeTerms(null, List.of("asma"));
		assertThat(ImportTermsMerger.mergeTerms(first, List.of("asma brônquica")))
				.containsExactly("asma", "asma brônquica");
	}

	@Test
	void mergeTerms_skipsDuplicateNormalizedTextOnLaterRow() {
		List<String> first = ImportTermsMerger.mergeTerms(null, List.of("asma"));
		assertThat(ImportTermsMerger.mergeTerms(first, List.of("asma")))
				.containsExactly("asma");
		assertThat(ImportTermsMerger.mergeTerms(first, List.of("  asma  ")))
				.containsExactly("asma");
	}

	@Test
	void mergeTerms_multiColumnFirstRowThenSingleColumnDuplicateRow() {
		List<String> first = ImportTermsMerger.mergeTerms(null, List.of("asma", "crónica"));
		assertThat(ImportTermsMerger.mergeTerms(first, List.of("bronquitis")))
				.containsExactly("asma", "crónica", "bronquitis");
	}

	@Test
	void mergeTerms_dedupesWithinFirstRow() {
		assertThat(ImportTermsMerger.mergeTerms(null, List.of("asma", "asma", "crónica")))
				.containsExactly("asma", "crónica");
	}

	@Test
	void mergeRowIntoMap_secondRowBecomesPtWhenFirstRowHadNoTerms() {
		Map<String, List<String>> termsByCode = new LinkedHashMap<>();
		ImportTermsMerger.mergeRowIntoMap(termsByCode, "100", List.of("bronquitis"));
		assertThat(termsByCode.get("100")).containsExactly("bronquitis");
	}

	@Test
	void mergeRowIntoMap_accumulatesInFileOrder() {
		Map<String, List<String>> termsByCode = new LinkedHashMap<>();
		ImportTermsMerger.mergeRowIntoMap(termsByCode, "100", List.of("asma"));
		ImportTermsMerger.mergeRowIntoMap(termsByCode, "100", List.of("asma brônquica"));
		ImportTermsMerger.mergeRowIntoMap(termsByCode, "100", List.of("asma"));
		ImportTermsMerger.mergeRowIntoMap(termsByCode, "200", List.of("gripe"));
		assertThat(termsByCode.get("100")).containsExactly("asma", "asma brônquica");
		assertThat(new ArrayList<>(termsByCode.keySet())).containsExactly("100", "200");
	}

	@Test
	void mergeTerms_emptyRowTermsReturnsExisting() {
		List<String> existing = List.of("asma", "crónica");
		assertThat(ImportTermsMerger.mergeTerms(existing, List.of()))
				.containsExactly("asma", "crónica");
	}
}
