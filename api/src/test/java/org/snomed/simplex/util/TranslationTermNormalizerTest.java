package org.snomed.simplex.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationTermNormalizerTest {

	@Test
	void normalize_replacesNonBreakingSpacesWithRegularSpace() {
		assertThat(TranslationTermNormalizer.normalize("a\u00A0b")).isEqualTo("a b");
		assertThat(TranslationTermNormalizer.normalize("\u202F")).isEmpty();
		assertThat(TranslationTermNormalizer.normalize("\u2007")).isEmpty();
	}

	@Test
	void normalize_removesZeroWidthCharacters() {
		assertThat(TranslationTermNormalizer.normalize("a\u200Bb")).isEqualTo("ab");
		assertThat(TranslationTermNormalizer.normalize("a\u200Cb")).isEqualTo("ab");
		assertThat(TranslationTermNormalizer.normalize("a\u200Db")).isEqualTo("ab");
		assertThat(TranslationTermNormalizer.normalize("a\u2060b")).isEqualTo("ab");
		assertThat(TranslationTermNormalizer.normalize("\uFEFFterm")).isEqualTo("term");
	}

	@Test
	void normalize_replacesEnAndEmDashWithHyphen() {
		assertThat(TranslationTermNormalizer.normalize("a\u2013b")).isEqualTo("a-b");
		assertThat(TranslationTermNormalizer.normalize("a\u2014b")).isEqualTo("a-b");
	}

	@Test
	void normalize_collapsesMultipleSpacesAndTrims() {
		assertThat(TranslationTermNormalizer.normalize("  hello   world  ")).isEqualTo("hello world");
	}

	@Test
	void normalize_nullOrOnlyBadCharactersReturnsEmpty() {
		assertThat(TranslationTermNormalizer.normalize(null)).isEmpty();
		assertThat(TranslationTermNormalizer.normalize("\u200B\u200C\u2060")).isEmpty();
		assertThat(TranslationTermNormalizer.normalize("   ")).isEmpty();
	}

	@Test
	void normalizeTerms_filtersNullBlankAndPostCleanupEmpty() {
		assertThat(TranslationTermNormalizer.normalizeTerms(null)).isEmpty();
		List<String> rawTerms = new ArrayList<>();
		rawTerms.add("  preferred  ");
		rawTerms.add(null);
		rawTerms.add("");
		rawTerms.add(" \u200B ");
		assertThat(TranslationTermNormalizer.normalizeTerms(rawTerms)).containsExactly("preferred");
	}
}
