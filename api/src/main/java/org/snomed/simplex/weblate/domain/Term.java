package org.snomed.simplex.weblate.domain;

import java.util.HashMap;
import java.util.Map;

public class Term {

	private final String term;
	private final Map<Long, Long> acceptabilityMap;

	public Term(String term) {
		this.term = term;
		acceptabilityMap = new HashMap<>();
	}

	public Term addAcceptability(long langRefset, long acceptability) {
		acceptabilityMap.put(langRefset, acceptability);
		return this;
	}

	public String getTerm() {
		return term;
	}

	public Map<Long, Long> getAcceptabilityMap() {
		return acceptabilityMap;
	}
}
