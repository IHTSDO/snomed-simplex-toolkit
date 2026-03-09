package org.snomed.simplex.translation.service;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.repository.TranslationStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to support merging of translations between sources.
 * For example merging translation work done in the Translation Tool and Snowstorm.
 */
@Service
public class TranslationMergeService {

	private final TranslationStateRepository stateRepository;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TranslationMergeService(TranslationStateRepository stateRepository) {
		this.stateRepository = stateRepository;
	}

	/**
	 * This merge function infers changes from the translation source and applies them to the translation target.
	 * The source or target can be the Translation Tool or Snowstorm.
	 * The previous state of each translation source is used to infer the type of change of each term.
	 * The final merged state is also persisted so it can be used to infer changes when the source and target are reversed later.
	 */
	void applyMerge(TranslationSource source, TranslationSource target, String languageCode, String langRefsetId) throws ServiceExceptionWithStatusCode {
		logger.info("TranslationMerge {}-{} Merging from {} to {}", languageCode, langRefsetId, source.getType(), target.getType());

		logger.info("TranslationMerge {}-{} Reading translations from {}", languageCode, langRefsetId, source.getType());
		TranslationState sourceState = source.readTranslation();
		logCounts(source, languageCode, langRefsetId, sourceState);

		logger.info("TranslationMerge {}-{} Reading translations from {}", languageCode, langRefsetId, target.getType());
		TranslationState targetState = target.readTranslation();
		logCounts(target, languageCode, langRefsetId, targetState);

		TranslationState previousSourceState = stateRepository.loadStateOrBlank(langRefsetId, source.getType());
		TranslationIntent sourceIntent = inferIntent(previousSourceState, sourceState);
		AtomicInteger additions = new AtomicInteger();
		AtomicInteger removals = new AtomicInteger();
		sourceIntent.getTermIntents().values().forEach(termIntent -> termIntent.forEach(term -> {
			if (term.intent() == Intent.ADD) {
				additions.incrementAndGet();
			} else if (term.intent() == Intent.REMOVE) {
				removals.incrementAndGet();
			}
		}));
		if (additions.get() > 0 || removals.get() > 0) {
			logger.info("TranslationMerge {}-{} Applying {} additions and {} removals", languageCode, langRefsetId, additions.get(), removals.get());
			applyIntent(targetState, sourceIntent);
			// Build an additions-only state to avoid re-uploading unchanged translations.
			// The full targetState is still saved to the repository for future diff operations.
			TranslationState additionsState = new TranslationState();
			Map<Long, List<String>> addedTerms = additionsState.getConceptTerms();
			for (Map.Entry<Long, List<TermIntent>> entry : sourceIntent.getTermIntents().entrySet()) {
				List<String> added = entry.getValue().stream()
						.filter(ti -> ti.intent() == Intent.ADD)
						.map(TermIntent::term)
						.toList();
				if (!added.isEmpty()) {
					addedTerms.put(entry.getKey(), added);
				}
			}
			target.writeTranslation(additionsState);
			stateRepository.saveState(langRefsetId, source.getType(), sourceState);
			stateRepository.saveState(langRefsetId, target.getType(), targetState);
			logger.info("TranslationMerge {}-{} Merging complete", languageCode, langRefsetId);
		} else {
			logger.info("TranslationMerge {}-{} No translation changes found", languageCode, langRefsetId);
		}
	}

	private void logCounts(TranslationSource source, String languageCode, String langRefsetId, TranslationState sourceState) {
		Map<Long, List<String>> stateConceptTerms = sourceState.getConceptTerms();
		int totalTerms = stateConceptTerms.values().stream().mapToInt(List::size).sum();
		if (logger.isInfoEnabled()) {
			logger.info("TranslationMerge {}-{} Found {} terms across {} concepts from {}",
				languageCode, langRefsetId, String.format("%,d", totalTerms), String.format("%,d", stateConceptTerms.size()), source.getType());
		}
	}

	TranslationIntent inferIntent(TranslationState previousState, TranslationState newState) {
		TranslationIntent intent = new TranslationIntent();
		Map<Long, List<TermIntent>> intents = intent.getTermIntents();
		Map<Long, List<String>> previousStateConceptTerms = previousState.getConceptTerms();
		Map<Long, List<String>> newStateConceptTerms = newState.getConceptTerms();
		Set<Long> allCodes = combineKeys(previousStateConceptTerms, newStateConceptTerms);
		for (Long code : allCodes) {
			List<String> previousTranslation = previousStateConceptTerms.get(code);
			List<String> newTerms = newStateConceptTerms.get(code);
			intents.put(code, inferIntentForConcept(previousTranslation, newTerms));
		}
		return intent;
	}

	void applyIntent(TranslationState currentState, TranslationIntent intent) {
		Map<Long, List<String>> currentMap = currentState.getConceptTerms();
		Map<Long, List<TermIntent>> intentMap = intent.getTermIntents();
		for (Map.Entry<Long, List<TermIntent>> conceptIntent : intentMap.entrySet()) {
			Long code = conceptIntent.getKey();
			List<String> terms = currentMap.computeIfAbsent(code, c -> new ArrayList<>());
			boolean ptFound = false;
			for (TermIntent termIntent : conceptIntent.getValue()) {
				Intent thisIntent = termIntent.intent();
				String term = termIntent.term();
				if (thisIntent == Intent.ADD) {
					if (!ptFound) {
						terms.add(0, term);
					} else {
						terms.add(term);
					}
					ptFound = true;
				} else if (thisIntent == Intent.NONE) {
					ptFound = true;
				} else if (thisIntent == Intent.REMOVE) {
					terms.remove(term);
				}
			}
			currentMap.put(code, terms);
		}
	}

	private List<TermIntent> inferIntentForConcept(List<String> previousTerms, List<String> newTerms) {
		List<TermIntent> termIntents = new ArrayList<>();
		if (previousTerms == null) {
			// No previous translation of this concept, all terms are ADD
			for (String term : newTerms) {
				termIntents.add(new TermIntent(term, Intent.ADD));
			}
		} else {
			for (String term : newTerms) {
				termIntents.add(new TermIntent(term, previousTerms.contains(term) ? Intent.NONE : Intent.ADD));
			}
			for (String term : previousTerms) {
				if (!newTerms.contains(term)) {
					termIntents.add(new TermIntent(term, Intent.REMOVE));
				}
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
