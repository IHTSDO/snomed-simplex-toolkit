package org.snomed.simplex.rest.pojos;

/**
 * Update request for RVF-related validation options stored on the code system branch.
 */
public class ValidationSettingsRequest {

	private boolean ignoreCase;

	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	public void setIgnoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
	}
}
