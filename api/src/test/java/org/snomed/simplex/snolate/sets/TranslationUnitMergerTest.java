package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationUnitMergerTest {

	private static final String COMPOSITE = "nl-31000172101";

	@Test
	void merge_unionsMemberOfAndAssignsCanonicalId() {
		TranslationUnit a = unit("63161005", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "auto-id-a");
		TranslationUnit b = unit("63161005", List.of("term"), TranslationStatus.COMPLETE, Set.of("BE_set"), "auto-id-b");

		List<String> warnings = new ArrayList<>();
		TranslationUnitMerger.MergeResult result = TranslationUnitMerger.merge(COMPOSITE, "63161005", List.of(a, b), warnings);

		assertThat(result.canonical().getId()).isEqualTo("nl-31000172101_63161005");
		assertThat(result.canonical().getMemberOf()).containsExactly("BE_set");
		assertThat(result.canonical().getTerms()).containsExactly("term");
		assertThat(result.canonical().getStatus()).isEqualTo(TranslationStatus.COMPLETE);
		assertThat(result.orphanDocumentIds()).containsExactlyInAnyOrder("auto-id-a", "auto-id-b");
	}

	@Test
	void needsRepair_singleDocWithWrongId() {
		TranslationUnit only = unit("100", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "wrong-id");
		assertThat(TranslationUnitMerger.needsRepair(COMPOSITE, "100", List.of(only))).isTrue();
	}

	@Test
	void needsRepair_canonicalDocUnchanged() {
		TranslationUnit only = unit("100", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "nl-31000172101_100");
		assertThat(TranslationUnitMerger.needsRepair(COMPOSITE, "100", List.of(only))).isFalse();
	}

	private static TranslationUnit unit(String code, List<String> terms, TranslationStatus status, Set<String> memberOf, String id) {
		TranslationUnit u = new TranslationUnit(
				new TranslationUnit.MembershipKey(code, "31000172101", "nl", COMPOSITE, 1),
				terms, status, new LinkedHashSet<>(memberOf));
		u.setId(id);
		return u;
	}
}
