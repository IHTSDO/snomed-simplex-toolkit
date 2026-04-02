package org.snomed.simplex.translation.rf2;

import java.util.HashMap;
import java.util.Map;

public class Rf2Term {

	private final String termString;
	private final Map<Long, Long> acceptabilityMap;

	public Rf2Term(String termString) {
		this.termString = termString;
		acceptabilityMap = new HashMap<>();
	}

	public Rf2Term addAcceptability(long langRefset, long acceptability) {
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
