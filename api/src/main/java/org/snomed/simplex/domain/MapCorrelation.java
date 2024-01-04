package org.snomed.simplex.domain;

public enum MapCorrelation {

	TARGET_EQUIVALENT("1193548004"),// 1193548004 |Exact match between map source and map target (foundation metadata concept)|
	TARGET_BROADER("1193549007"),// 1193549007 |Narrow map source to broad map target (foundation metadata concept)|
	TARGET_NARROWER("1193547009"),// 1193547009 |Broad map source to narrow map target (foundation metadata concept)|
	TARGET_INEXACT("1193550007"),// 1193550007 |Partial overlap between map source and target (foundation metadata concept)|
	TARGET_NOT_MAPPABLE("1193551006"),// 1193551006 |Map source not mappable to map target (foundation metadata concept)|
	NOT_SPECIFIED("1193552004");// 1193552004 |Map source to map target correlation not specified (foundation metadata concept)|

	private final String conceptId;

	MapCorrelation(String conceptId) {
		this.conceptId = conceptId;
	}

	public static MapCorrelation fromConceptId(String correlationId) {
		for (MapCorrelation value : values()) {
			if (value.getConceptId().equals(correlationId)) {
				return value;
			}
		}
		return null;
	}

	public String getConceptId() {
		return conceptId;
	}

}
