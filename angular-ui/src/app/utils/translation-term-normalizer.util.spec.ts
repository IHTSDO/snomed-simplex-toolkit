import {
	normalizeTranslationTerm,
	normalizeTranslationTerms
} from './translation-term-normalizer.util';

describe('translation-term-normalizer.util', () => {
	it('normalizeTranslationTerm replaces non-breaking spaces with regular space', () => {
		expect(normalizeTranslationTerm('a\u00A0b')).toBe('a b');
		expect(normalizeTranslationTerm('\u202F')).toBe('');
		expect(normalizeTranslationTerm('\u2007')).toBe('');
	});

	it('normalizeTranslationTerm removes zero-width characters', () => {
		expect(normalizeTranslationTerm('a\u200Bb')).toBe('ab');
		expect(normalizeTranslationTerm('a\u200Cb')).toBe('ab');
		expect(normalizeTranslationTerm('a\u200Db')).toBe('ab');
		expect(normalizeTranslationTerm('a\u2060b')).toBe('ab');
		expect(normalizeTranslationTerm('\uFEFFterm')).toBe('term');
	});

	it('normalizeTranslationTerm replaces en and em dash with hyphen', () => {
		expect(normalizeTranslationTerm('a\u2013b')).toBe('a-b');
		expect(normalizeTranslationTerm('a\u2014b')).toBe('a-b');
	});

	it('normalizeTranslationTerm collapses multiple spaces and trims', () => {
		expect(normalizeTranslationTerm('  hello   world  ')).toBe('hello world');
	});

	it('normalizeTranslationTerm handles nullish and only bad characters', () => {
		expect(normalizeTranslationTerm(null)).toBe('');
		expect(normalizeTranslationTerm(undefined)).toBe('');
		expect(normalizeTranslationTerm('\u200B\u200C\u2060')).toBe('');
		expect(normalizeTranslationTerm('   ')).toBe('');
	});

	it('normalizeTranslationTerms filters null blank and post-cleanup empty', () => {
		expect(normalizeTranslationTerms(null)).toEqual([]);
		expect(normalizeTranslationTerms(['  preferred  ', null as unknown as string, '', ' \u200B '])).toEqual(['preferred']);
	});
});
