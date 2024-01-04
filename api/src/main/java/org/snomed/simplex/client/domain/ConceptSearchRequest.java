package org.snomed.simplex.client.domain;

import java.util.Collection;

public class ConceptSearchRequest {

	private Collection<String> conceptIds;
	private Boolean activeFilter;
	private boolean returnIdOnly;
	private int limit;

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

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public int getLimit() {
		return limit;
	}
}
