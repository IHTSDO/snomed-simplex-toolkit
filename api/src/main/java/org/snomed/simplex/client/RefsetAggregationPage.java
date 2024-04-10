package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.ConceptMini;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RefsetAggregationPage {

	private Map<String, Long> memberCountsByReferenceSet;
	private Map<String, ConceptMini> referenceSets;

	public List<ConceptMini> getRefsetsWithActiveMemberCount() {
		Collection<ConceptMini> values = referenceSets.values();
		values.forEach(refset -> refset.setActiveMemberCount(memberCountsByReferenceSet.getOrDefault(refset.getConceptId(), 0L)));
		return new ArrayList<>(values);
	}

	public Map<String, Long> getMemberCountsByReferenceSet() {
		return memberCountsByReferenceSet;
	}

	public Map<String, ConceptMini> getReferenceSets() {
		return referenceSets;
	}
}
