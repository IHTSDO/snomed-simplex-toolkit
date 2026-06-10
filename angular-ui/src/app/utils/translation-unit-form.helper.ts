export interface TranslationUpdateBody {
	terms: string[];
	status: string;
}

export interface StatusSyncResult {
	status: string;
	disabled: boolean;
}

export function normalizeTargetTerms(target: string[]): string[] {
	return (Array.isArray(target) ? target : [])
		.map((t) => (t ?? '').trim())
		.filter((t) => t.length > 0);
}

export function currentTargetTerms(primary: string, synonyms: string[]): string[] {
	const p = primary.trim();
	const synVals = synonyms.map((s) => s.trim()).filter((x) => x.length > 0);
	return p ? [p, ...synVals] : synVals;
}

export function isTranslationEmpty(primary: string, synonyms: string[]): boolean {
	if (primary.trim().length > 0) {
		return false;
	}
	return !synonyms.some((s) => s.trim().length > 0);
}

export function termsChangedFromLoaded(current: string[], loaded: string[]): boolean {
	if (current.length !== loaded.length) {
		return true;
	}
	return current.some((term, index) => term !== loaded[index]);
}

export function buildUpdateBody(
	primary: string,
	synonyms: string[],
	status: string
): TranslationUpdateBody {
	const synVals = synonyms.map((s) => s.trim()).filter((x) => x.length > 0);
	const p = primary.trim();
	const terms = p ? [p, ...synVals] : synVals;
	const resolvedStatus = isTranslationEmpty(primary, synonyms) ? 'NOT_STARTED' : (status ?? 'FOR_REVIEW');
	return { terms, status: resolvedStatus };
}

/**
 * Empty translation ⇒ status {@code NOT_STARTED} and disabled control.
 * {@code COMPLETE} with unchanged terms ⇒ disabled read-only.
 * With terms ⇒ enabled; {@code NOT_STARTED} or edited {@code COMPLETE} default to {@code FOR_REVIEW}.
 */
export function syncStatusWithTranslationText(
	status: string,
	primary: string,
	synonyms: string[],
	loadedTerms: string[]
): StatusSyncResult {
	const empty = isTranslationEmpty(primary, synonyms);
	if (empty) {
		return { status: 'NOT_STARTED', disabled: true };
	}
	const current = currentTargetTerms(primary, synonyms);
	if (status === 'COMPLETE' && !termsChangedFromLoaded(current, loadedTerms)) {
		return { status: 'COMPLETE', disabled: true };
	}
	let nextStatus = status;
	if (status === 'NOT_STARTED' || status === 'COMPLETE') {
		nextStatus = 'FOR_REVIEW';
	}
	return { status: nextStatus, disabled: false };
}
