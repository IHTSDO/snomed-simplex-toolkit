package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptListSupportTest {

	@Test
	void joinAndSplitConceptList_roundTrip() {
		List<String> ids = List.of("123456789", "987654321");
		String joined = ConceptListSupport.joinConceptList(ids);
		assertThat(ConceptListSupport.splitConceptList(joined)).containsExactlyElementsOf(ids);
	}

	@Test
	void normaliseConceptIdCell_acceptsPlainAndPipeDelimitedValues() {
		assertThat(ConceptListSupport.normaliseConceptIdCell("123456789")).isEqualTo("123456789");
		assertThat(ConceptListSupport.normaliseConceptIdCell("123456789 |Some term|")).isEqualTo("123456789");
		assertThat(ConceptListSupport.normaliseConceptIdCell("abc")).isNull();
	}

	@Test
	void dedupeConceptIds_skipsInvalidAndDuplicateRows() {
		ConceptListSupport.ConceptListParseResult result = ConceptListSupport.dedupeConceptIds(
				List.of("111111111", "111111111", "222222222", "bad", "  "));
		assertThat(result.conceptIds()).containsExactly("111111111", "222222222");
		assertThat(result.duplicateRows()).isEqualTo(1);
		assertThat(result.invalidRows()).isEqualTo(1);
	}
}
