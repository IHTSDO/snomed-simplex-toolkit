package com.snomed.derivativemanagementtool.client.domain;

import java.util.Collection;

public class ConceptSearchRequest {

	private Collection<String> conceptIds;
	private Boolean activeFilter;
	private boolean returnIdOnly;

	public ConceptSearchRequest(Collection<String> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public Collection<String> getConceptIds() {
		return conceptIds;
	}

	public void setConceptIds(Collection<String> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public Boolean isActiveFilter() {
		return activeFilter;
	}

	public void setActiveFilter(Boolean activeFilter) {
		this.activeFilter = activeFilter;
	}

	public boolean isReturnIdOnly() {
		return returnIdOnly;
	}

	public void setReturnIdOnly(boolean returnIdOnly) {
		this.returnIdOnly = returnIdOnly;
	}
}
