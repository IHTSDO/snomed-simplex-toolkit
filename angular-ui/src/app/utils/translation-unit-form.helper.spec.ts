import { buildUpdateBody, currentTargetTerms, isTranslationEmpty } from './translation-unit-form.helper';

describe('translation-unit-form.helper', () => {
	it('buildUpdateBody normalizes primary and synonyms and drops empty post-cleanup terms', () => {
		const body = buildUpdateBody('  asma\u00A0cr\u00F3nica  ', [' \u200B ', 'syn\u2013onym'], 'FOR_REVIEW');
		expect(body.terms).toEqual(['asma cr\u00F3nica', 'syn-onym']);
		expect(body.status).toBe('FOR_REVIEW');
	});

	it('buildUpdateBody sets NOT_STARTED when all terms normalize to empty', () => {
		const body = buildUpdateBody(' \u200B ', ['   '], 'FOR_REVIEW');
		expect(body.terms).toEqual([]);
		expect(body.status).toBe('NOT_STARTED');
	});

	it('currentTargetTerms returns normalized preferred then synonyms', () => {
		expect(currentTargetTerms('  a\u00A0b  ', [' c\u2013d '])).toEqual(['a b', 'c-d']);
	});

	it('isTranslationEmpty uses normalized values', () => {
		expect(isTranslationEmpty(' \u200B ', ['   '])).toBe(true);
		expect(isTranslationEmpty('term', ['   '])).toBe(false);
	});
});
