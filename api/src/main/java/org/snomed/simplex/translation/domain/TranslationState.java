package org.snomed.simplex.translation.domain;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.List;
import java.util.Map;

/**
 * Snapshot of a translation of one language refset including a set of concepts and their terms.
 * The terms are an order set with the first one being the PT for the relevant language refset.
 * Used to capture a dump from Snowstorm, the Translation Tool, or another source.
 * Used for a diff operation at a later date to discover user intent.
 */
public class TranslationState {

	private final Map<Long, List<String>> conceptTerms;

	public TranslationState() {
		conceptTerms = new Long2ObjectOpenHashMap<>();
	}

	public Map<Long, List<String>> getConceptTerms() {
		return conceptTerms;
	}
}
