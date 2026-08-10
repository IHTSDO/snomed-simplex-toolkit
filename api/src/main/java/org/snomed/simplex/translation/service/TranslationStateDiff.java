package org.snomed.simplex.translation.service;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;

import java.util.*;

/**
 * Diff and apply helpers for {@link TranslationState} snapshots.
 */
public final class TranslationStateDiff {

	private TranslationStateDiff() {
	}

	public static TranslationIntent diff(TranslationState previous, TranslationState current) {
		TranslationIntent intent = new TranslationIntent();
		Map<Long, List<TermIntent>> intents = intent.getTermIntents();
		Map<Long, List<String>> previousConceptTerms = previous.getConceptTerms();
		Map<Long, List<String>> currentConceptTerms = current.getConceptTerms();
		Set<Long> allCodes = combineKeys(previousConceptTerms, currentConceptTerms);
		for (Long code : allCodes) {
			List<String> previousTerms = previousConceptTerms.getOrDefault(code, Collections.emptyList());
			List<String> currentTerms = currentConceptTerms.getOrDefault(code, Collections.emptyList());
			intents.put(code, inferIntentForConcept(previousTerms, currentTerms));
		}
		return intent;
	}

	public static boolean hasAdditionsOrRemovals(TranslationIntent intent) {
		return intent.getTermIntents().values().stream()
				.flatMap(Collection::stream)
				.anyMatch(ti -> ti.intent() == Intent.ADD || ti.intent() == Intent.REMOVE);
	}

	public static List<String> termsForConcept(TranslationState state, Long conceptId) {
		return state.getConceptTerms().getOrDefault(conceptId, List.of());
	}

	public static boolean isSnowstormAdd(TranslationIntent delta, Long conceptId, String term) {
		return intentForTerm(delta, conceptId, term) == Intent.ADD;
	}

	public static boolean isSnowstormRemove(TranslationIntent delta, Long conceptId, String term) {
		return intentForTerm(delta, conceptId, term) == Intent.REMOVE;
	}

	public static Intent intentForTerm(TranslationIntent intent, Long conceptId, String term) {
		return intent.getTermIntents().getOrDefault(conceptId, List.of()).stream()
				.filter(ti -> ti.term().equals(term))
				.map(TermIntent::intent)
				.findFirst()
				.orElse(Intent.NONE);
	}

	public static TranslationState copy(TranslationState source) {
		TranslationState copy = new TranslationState();
		for (Map.Entry<Long, List<String>> entry : source.getConceptTerms().entrySet()) {
			copy.getConceptTerms().put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return copy;
	}

	/**
	 * Apply ADD/REMOVE intents onto a term list. First ADD is treated as PT (index 0).
	 */
	public static List<String> applyIntent(List<String> existingTerms, List<TermIntent> termIntents) {
		List<String> terms = new ArrayList<>(existingTerms);
		boolean ptFound = !terms.isEmpty();
		for (TermIntent termIntent : termIntents) {
			Intent thisIntent = termIntent.intent();
			String term = termIntent.term();
			if (thisIntent == Intent.ADD) {
				applyAdd(terms, term, ptFound);
				ptFound = true;
			} else if (thisIntent == Intent.NONE) {
				ptFound = true;
			} else if (thisIntent == Intent.REMOVE) {
				terms.remove(term);
			}
		}
		return terms;
	}

	private static void applyAdd(List<String> terms, String term, boolean ptFound) {
		int existingIndex = terms.indexOf(term);
		if (existingIndex >= 0) {
			if (!ptFound && existingIndex > 0) {
				terms.remove(existingIndex);
				terms.add(0, term);
			}
			return;
		}
		if (!ptFound) {
			terms.add(0, term);
		} else {
			terms.add(term);
		}
	}

	private static List<TermIntent> inferIntentForConcept(List<String> previousTerms, List<String> currentTerms) {
		List<TermIntent> termIntents = new ArrayList<>();
		for (String term : currentTerms) {
			termIntents.add(new TermIntent(term, previousTerms.contains(term) ? Intent.NONE : Intent.ADD));
		}
		for (String term : previousTerms) {
			if (!currentTerms.contains(term)) {
				termIntents.add(new TermIntent(term, Intent.REMOVE));
			}
		}
		return termIntents;
	}

	private static Set<Long> combineKeys(Map<Long, ?> mapA, Map<Long, ?> mapB) {
		Set<Long> allCodes = new LongOpenHashSet(mapA.keySet());
		allCodes.addAll(mapB.keySet());
		return allCodes;
	}

}
