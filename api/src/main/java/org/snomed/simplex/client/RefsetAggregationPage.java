package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.ConceptMini;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RefsetAggregationPage {

	private Map<String, Long> memberCountsByReferenceSet;
	private Map<String, ConceptMini> referenceSets;

	public List<ConceptMini> getRefsetsWithActiveMemberCount() {
		return referenceSets.values().stream()
				.peek(refset -> refset.setActiveMemberCount(memberCountsByReferenceSet.getOrDefault(refset.getConceptId(), 0L)))
				.collect(Collectors.toList());
	}

	public Map<String, Long> getMemberCountsByReferenceSet() {
		return memberCountsByReferenceSet;
	}

	public Map<String, ConceptMini> getReferenceSets() {
		return referenceSets;
	}
}
