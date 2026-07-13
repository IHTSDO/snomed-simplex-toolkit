import {
	loadTranslationStudioHierarchyPreferences,
	saveTranslationStudioHierarchyPreferences
} from './translation-studio-hierarchy-preferences';

const STORAGE_KEY = 'simplex.translation-studio.hierarchy-preferences';

describe('translation-studio-hierarchy-preferences', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it('returns defaults when storage is empty', () => {
		expect(loadTranslationStudioHierarchyPreferences()).toEqual({
			showHierarchyTranslationTerms: false,
			showFullPartialHierarchy: false
		});
	});

	it('round-trips saved values', () => {
		saveTranslationStudioHierarchyPreferences({
			showHierarchyTranslationTerms: true,
			showFullPartialHierarchy: true
		});

		expect(loadTranslationStudioHierarchyPreferences()).toEqual({
			showHierarchyTranslationTerms: true,
			showFullPartialHierarchy: true
		});
		expect(localStorage.getItem(STORAGE_KEY)).toBe(
			JSON.stringify({
				showHierarchyTranslationTerms: true,
				showFullPartialHierarchy: true
			})
		);
	});

	it('falls back to defaults for corrupt JSON', () => {
		localStorage.setItem(STORAGE_KEY, '{not-json');

		expect(loadTranslationStudioHierarchyPreferences()).toEqual({
			showHierarchyTranslationTerms: false,
			showFullPartialHierarchy: false
		});
	});

	it('falls back to defaults for wrong types', () => {
		localStorage.setItem(
			STORAGE_KEY,
			JSON.stringify({
				showHierarchyTranslationTerms: 'yes',
				showFullPartialHierarchy: 1
			})
		);

		expect(loadTranslationStudioHierarchyPreferences()).toEqual({
			showHierarchyTranslationTerms: false,
			showFullPartialHierarchy: false
		});
	});

	it('merges partial saves with existing stored preferences', () => {
		saveTranslationStudioHierarchyPreferences({ showHierarchyTranslationTerms: true });

		saveTranslationStudioHierarchyPreferences({ showFullPartialHierarchy: true });

		expect(loadTranslationStudioHierarchyPreferences()).toEqual({
			showHierarchyTranslationTerms: true,
			showFullPartialHierarchy: true
		});
	});
});
