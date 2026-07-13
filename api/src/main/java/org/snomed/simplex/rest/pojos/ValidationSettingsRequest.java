package org.snomed.simplex.rest.pojos;

/**
 * Update request for RVF-related validation options stored on the code system branch.
 */
public class ValidationSettingsRequest {

	private boolean ignoreCase;
	private boolean conceptsMaintainedExternally;

	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	public void setIgnoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
	}

	public boolean isConceptsMaintainedExternally() {
		return conceptsMaintainedExternally;
	}

	public void setConceptsMaintainedExternally(boolean conceptsMaintainedExternally) {
		this.conceptsMaintainedExternally = conceptsMaintainedExternally;
	}
}
