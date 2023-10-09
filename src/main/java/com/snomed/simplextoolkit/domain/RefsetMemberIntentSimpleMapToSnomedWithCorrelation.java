package com.snomed.simplextoolkit.domain;

public class RefsetMemberIntentSimpleMapToSnomedWithCorrelation extends RefsetMemberIntent {

	private final String sourceCode;
	private final MapCorrelation correlation;

	public RefsetMemberIntentSimpleMapToSnomedWithCorrelation(String sourceCode, String targetSnomedCode, MapCorrelation correlationId) {
		super(targetSnomedCode);
		this.sourceCode = sourceCode;
		this.correlation = correlationId;
	}

	public String getCorrelationIdOrNull() {
		return correlation != null ? correlation.getConceptId() : null;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public MapCorrelation getCorrelation() {
		return correlation;
	}
}
