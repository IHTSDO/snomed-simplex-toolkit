package org.snomed.simplex.translation.domain;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.List;
import java.util.Map;

/**
 * Holder for a whole set of concepts and their terms, with the user intent for each term.
 */
public class TranslationIntent {

	private final Map<Long, List<TermIntent>> termIntents;

	public TranslationIntent() {
		termIntents = new Long2ObjectOpenHashMap<>();
	}

	public Map<Long, List<TermIntent>> getTermIntents() {
		return termIntents;
	}
}
