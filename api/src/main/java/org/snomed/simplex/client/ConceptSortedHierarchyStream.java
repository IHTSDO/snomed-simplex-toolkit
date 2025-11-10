package org.snomed.simplex.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.ConceptMini;

import java.util.*;
import java.util.function.Supplier;

import static java.lang.Boolean.TRUE;

public class ConceptSortedHierarchyStream implements Supplier<ConceptMini> {

	private final String branch;
	private final String focusConcept;
	private final SnowstormClient snowstormClient;

	private Deque<List<ConceptMini>> stack = null;
	private final Set<Long> coveredConcepts = new LongOpenHashSet();
	private int apiHits;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ConceptSortedHierarchyStream(String branch, String focusConcept, SnowstormClient snowstormClient) {
		this.branch = branch;
		this.focusConcept = focusConcept;
		this.snowstormClient = snowstormClient;
		apiHits = 0;
	}

	@Override
	public ConceptMini get() {
		if (stack == null) {
			// Create stack with just focus concept
			stack = new ArrayDeque<>();
			List<ConceptMini> conceptList = snowstormClient.getConceptList(branch, focusConcept);
			apiHits++;
			stack.push(conceptList);
		}

		ConceptMini nextConcept;
		do {
			nextConcept = getNextConceptFromStack(stack);
			if (nextConcept == null) {
				logger.info("Completed concept stream using focus concept {}, {} API calls were made.", focusConcept, apiHits);
				return null;
			}
		} while(coveredConcepts.contains(Long.parseLong(nextConcept.getConceptId())));

		// Load children of this concept and add to list
		Object isLeafInferred = nextConcept.getExtraFields().get("isLeafInferred");
		if (isLeafInferred != TRUE) {
			Supplier<ConceptMini> children = snowstormClient.getConceptStream(branch, "<!" + nextConcept.getConceptId());
			apiHits++;
			List<ConceptMini> childrenList = new ArrayList<>();
			ConceptMini child;
			while ((child = children.get()) != null) {
				childrenList.add(child);
			}
			if (!childrenList.isEmpty()) {
				childrenList.sort(Comparator.comparing(c -> c.getPtOrFsnOrConceptId().toLowerCase()));
				stack.push(childrenList);
			}
		}

		coveredConcepts.add(Long.parseLong(nextConcept.getConceptId()));

		if (coveredConcepts.size() % 1_000 == 0) {
			logger.info("Fetched {} concepts", coveredConcepts.size());
		}

		// Return this concept
		return nextConcept;
	}

	private ConceptMini getNextConceptFromStack(Deque<List<ConceptMini>> stack) {
		List<ConceptMini> deepestList = null;
		while (!stack.isEmpty() && (deepestList = stack.peek()) != null && deepestList.isEmpty()) {
			stack.pop();
		}
		if (deepestList == null || deepestList.isEmpty()) {
			return null;
		}
		return deepestList.remove(0);
	}

}
