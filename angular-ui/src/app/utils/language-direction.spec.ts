import { isRtlLanguageCode, textDirection } from './language-direction';

describe('language-direction', () => {
	describe('isRtlLanguageCode', () => {
		it('returns true for RTL ISO codes', () => {
			expect(isRtlLanguageCode('ar')).toBe(true);
			expect(isRtlLanguageCode('he')).toBe(true);
			expect(isRtlLanguageCode('fa')).toBe(true);
			expect(isRtlLanguageCode('ur')).toBe(true);
		});

		it('is case-insensitive and accepts region tags', () => {
			expect(isRtlLanguageCode('AR')).toBe(true);
			expect(isRtlLanguageCode('ar-SA')).toBe(true);
			expect(isRtlLanguageCode('he_IL')).toBe(true);
		});

		it('returns false for LTR codes and empty values', () => {
			expect(isRtlLanguageCode('en')).toBe(false);
			expect(isRtlLanguageCode('nl')).toBe(false);
			expect(isRtlLanguageCode('')).toBe(false);
			expect(isRtlLanguageCode(null)).toBe(false);
			expect(isRtlLanguageCode(undefined)).toBe(false);
			expect(isRtlLanguageCode('   ')).toBe(false);
		});
	});

	describe('textDirection', () => {
		it('returns rtl for RTL languages and ltr otherwise', () => {
			expect(textDirection('ar')).toBe('rtl');
			expect(textDirection('en')).toBe('ltr');
			expect(textDirection(null)).toBe('ltr');
		});
	});
});
