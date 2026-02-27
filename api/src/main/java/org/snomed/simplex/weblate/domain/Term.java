package org.snomed.simplex.weblate.domain;

import java.util.HashMap;
import java.util.Map;

public class Term {

	private final String termString;
	private final Map<Long, Long> acceptabilityMap;

	public Term(String termString) {
		this.termString = termString;
		acceptabilityMap = new HashMap<>();
	}

	public Term addAcceptability(long langRefset, long acceptability) {
		acceptabilityMap.put(langRefset, acceptability);
		return this;
	}

	public String getTermString() {
		return termString;
	}

	public Map<Long, Long> getAcceptabilityMap() {
		return acceptabilityMap;
	}
}
