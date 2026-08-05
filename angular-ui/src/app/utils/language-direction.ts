/** ISO 639-1 (and common 639-3) codes that are written right-to-left. */
const RTL_LANGUAGE_CODES = new Set<string>([
	'ar',
	'he',
	'fa',
	'ur',
	'yi',
	'dv',
	'ku',
	'ps',
	'sd',
	'ug',
	'ckb',
	'arc'
]);

export type TextDirection = 'rtl' | 'ltr';

/** True when the ISO language code is typically written right-to-left. */
export function isRtlLanguageCode(code: string | null | undefined): boolean {
	if (!code) {
		return false;
	}
	const normalized = code.trim().toLowerCase();
	if (!normalized) {
		return false;
	}
	// Accept BCP-47 tags like "ar-SA" by checking the primary subtag.
	const primary = normalized.split(/[-_]/)[0];
	return RTL_LANGUAGE_CODES.has(primary);
}

/** Returns `rtl` or `ltr` for the given ISO language code. */
export function textDirection(code: string | null | undefined): TextDirection {
	return isRtlLanguageCode(code) ? 'rtl' : 'ltr';
}
