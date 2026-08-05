/**
 * Normalizes SNOMED translation term text. Keep in sync with
 * {@code api/src/main/java/org/snomed/simplex/util/TranslationTermNormalizer.java}.
 */
const NON_BREAKING_SPACE = '\u00A0';
const NARROW_NON_BREAKING_SPACE = '\u202F';
const FIGURE_SPACE = '\u2007';
const ZERO_WIDTH_SPACE = '\u200B';
const ZERO_WIDTH_NON_JOINER = '\u200C';
const ZERO_WIDTH_JOINER = '\u200D';
const WORD_JOINER = '\u2060';
const ZERO_WIDTH_NON_BREAKING_SPACE = '\uFEFF';
const EN_DASH = '\u2013';
const EM_DASH = '\u2014';
const SPACE = ' ';
const DASH = '-';

export function normalizeTranslationTerm(term: string | null | undefined): string {
	if (term == null) {
		return '';
	}
	let fixedTerm = term
		.replaceAll(NON_BREAKING_SPACE, SPACE)
		.replaceAll(NARROW_NON_BREAKING_SPACE, SPACE)
		.replaceAll(FIGURE_SPACE, SPACE)
		.replaceAll(ZERO_WIDTH_SPACE, '')
		.replaceAll(ZERO_WIDTH_NON_JOINER, '')
		.replaceAll(ZERO_WIDTH_JOINER, '')
		.replaceAll(WORD_JOINER, '')
		.replaceAll(ZERO_WIDTH_NON_BREAKING_SPACE, '')
		.replaceAll(EN_DASH, DASH)
		.replaceAll(EM_DASH, DASH);
	fixedTerm = fixedTerm.replace(/ +/g, SPACE);
	return fixedTerm.trim();
}

export function normalizeTranslationTerms(terms: string[] | null | undefined): string[] {
	if (!Array.isArray(terms) || terms.length === 0) {
		return [];
	}
	return terms
		.filter((term) => term != null)
		.map((term) => normalizeTranslationTerm(term))
		.filter((term) => term.length > 0);
}
