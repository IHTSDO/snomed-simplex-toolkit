package com.snomed.simplextoolkit.client.domain;

public class AlternateIdentifier {

	private String alternateIdentifier;
	private String identifierSchemeId;
	private boolean active;
	private String moduleId;
	private String referencedComponentId;

	public String getAlternateIdentifier() {
		return alternateIdentifier;
	}

	public String getIdentifierSchemeId() {
		return identifierSchemeId;
	}

	public boolean isActive() {
		return active;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}
}
