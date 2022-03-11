package com.snomed.simpleextensiontoolkit.domain;

public class SheetRefsetMemberSimpleMapToSnomedWithCorrelation extends SheetRefsetMember {

	private final String sourceCode;
	private final MapCorrelation correlation;

	public SheetRefsetMemberSimpleMapToSnomedWithCorrelation(String sourceCode, String targetSnomedCode, MapCorrelation correlationId) {
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
