export interface TranslationStudioHierarchyPreferences {
	showHierarchyTranslationTerms: boolean;
	showFullPartialHierarchy: boolean;
}

const STORAGE_KEY = 'simplex.translation-studio.hierarchy-preferences';

const DEFAULT_PREFERENCES: TranslationStudioHierarchyPreferences = {
	showHierarchyTranslationTerms: false,
	showFullPartialHierarchy: false
};

function parseBoolean(value: unknown): boolean | undefined {
	return typeof value === 'boolean' ? value : undefined;
}

function normalizePreferences(value: unknown): TranslationStudioHierarchyPreferences {
	if (value == null || typeof value !== 'object') {
		return { ...DEFAULT_PREFERENCES };
	}
	const raw = value as Record<string, unknown>;
	const showHierarchyTranslationTerms =
		parseBoolean(raw['showHierarchyTranslationTerms']) ?? DEFAULT_PREFERENCES.showHierarchyTranslationTerms;
	const showFullPartialHierarchy =
		parseBoolean(raw['showFullPartialHierarchy']) ?? DEFAULT_PREFERENCES.showFullPartialHierarchy;
	return { showHierarchyTranslationTerms, showFullPartialHierarchy };
}

export function loadTranslationStudioHierarchyPreferences(): TranslationStudioHierarchyPreferences {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (!raw) {
			return { ...DEFAULT_PREFERENCES };
		}
		return normalizePreferences(JSON.parse(raw));
	} catch {
		return { ...DEFAULT_PREFERENCES };
	}
}

export function saveTranslationStudioHierarchyPreferences(
	partial: Partial<TranslationStudioHierarchyPreferences>
): void {
	try {
		const merged = {
			...loadTranslationStudioHierarchyPreferences(),
			...partial
		};
		localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
	} catch {
		// Ignore quota / private-browsing errors; toggles still work in-memory.
	}
}
