package org.snomed.simplex.snolate.service;

import org.snomed.simplex.util.TranslationTermNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges translation terms from duplicate import rows for the same concept code.
 * The first row defines the preferred term; later rows contribute synonyms only.
 */
final class ImportTermsMerger {

	private ImportTermsMerger() {}

	static void mergeRowIntoMap(Map<String, List<String>> termsByCode, String conceptCode, List<String> rowTerms) {
		List<String> existing = termsByCode.get(conceptCode);
		termsByCode.put(conceptCode, mergeTerms(existing, rowTerms));
	}

	static List<String> mergeTerms(List<String> existing, List<String> rowTerms) {
		if (rowTerms == null || rowTerms.isEmpty()) {
			return existing != null ? new ArrayList<>(existing) : List.of();
		}
		if (existing == null || existing.isEmpty()) {
			return dedupeRowTerms(rowTerms);
		}
		List<String> merged = new ArrayList<>(existing);
		Set<String> seen = normalizedForms(merged);
		for (String term : rowTerms) {
			String normalized = TranslationTermNormalizer.normalize(term);
			if (!normalized.isEmpty() && seen.add(normalized)) {
				merged.add(normalized);
			}
		}
		return merged;
	}

	private static List<String> dedupeRowTerms(List<String> rowTerms) {
		List<String> result = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String term : rowTerms) {
			String normalized = TranslationTermNormalizer.normalize(term);
			if (!normalized.isEmpty() && seen.add(normalized)) {
				result.add(normalized);
			}
		}
		return result;
	}

	private static Set<String> normalizedForms(List<String> terms) {
		Set<String> seen = new LinkedHashSet<>();
		for (String term : terms) {
			String normalized = TranslationTermNormalizer.normalize(term);
			if (!normalized.isEmpty()) {
				seen.add(normalized);
			}
		}
		return seen;
	}
}
