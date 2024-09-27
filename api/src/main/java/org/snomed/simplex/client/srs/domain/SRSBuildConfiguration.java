package org.snomed.simplex.client.srs.domain;

public record SRSBuildConfiguration(String effectiveTime) {

	public String getEffectiveTime() {
		return effectiveTime != null ? effectiveTime.replaceAll("-", "") : null;
	}

}
