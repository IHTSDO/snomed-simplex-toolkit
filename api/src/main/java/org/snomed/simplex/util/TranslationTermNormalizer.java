package org.snomed.simplex.util;

import java.util.List;
import java.util.Objects;

/**
 * Normalizes SNOMED translation term text. Keep in sync with
 * {@code angular-ui/src/app/utils/translation-term-normalizer.util.ts}.
 */
public final class TranslationTermNormalizer {

	private static final String NON_BREAKING_SPACE_CHARACTER = "\u00A0";
	private static final String NARROW_NON_BREAKING_SPACE_CHARACTER = "\u202F";
	private static final String FIGURE_SPACE_CHARACTER = "\u2007";
	private static final String ZERO_WIDTH_SPACE = "\u200B";
	private static final String ZERO_WIDTH_NON_JOINER = "\u200C";
	private static final String ZERO_WIDTH_JOINER = "\u200D";
	private static final String WORD_JOINER = "\u2060";
	private static final String ZERO_WIDTH_NON_BREAKING_SPACE = "\uFEFF";
	private static final String EN_DASH = "–";
	private static final String EM_DASH = "—";
	private static final String SPACE = " ";
	private static final String DASH = "-";

	private TranslationTermNormalizer() {}

	public static String normalize(String term) {
		if (term == null) {
			return "";
		}
		String fixedTerm = term
				.replace(NON_BREAKING_SPACE_CHARACTER, SPACE)
				.replace(NARROW_NON_BREAKING_SPACE_CHARACTER, SPACE)
				.replace(FIGURE_SPACE_CHARACTER, SPACE)
				.replace(ZERO_WIDTH_SPACE, "")
				.replace(ZERO_WIDTH_NON_JOINER, "")
				.replace(ZERO_WIDTH_JOINER, "")
				.replace(WORD_JOINER, "")
				.replace(ZERO_WIDTH_NON_BREAKING_SPACE, "")
				.replace(EN_DASH, DASH)
				.replace(EM_DASH, DASH);
		fixedTerm = fixedTerm.replaceAll(" +", " ");
		return fixedTerm.trim();
	}

	public static List<String> normalizeTerms(List<String> rawTerms) {
		if (rawTerms == null || rawTerms.isEmpty()) {
			return List.of();
		}
		return rawTerms.stream()
				.filter(Objects::nonNull)
				.map(TranslationTermNormalizer::normalize)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
