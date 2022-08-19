package com.snomed.simplextoolkit.client.domain;

import java.util.List;

public class Axiom {

	private String moduleId;
	private String definitionStatus;
	private List<Relationship> relationships;

	public Axiom() {
	}

	public Axiom(String definitionStatus, List<Relationship> relationships) {
		this.definitionStatus = definitionStatus;
		this.relationships = relationships;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getDefinitionStatus() {
		return definitionStatus;
	}

	public void setDefinitionStatus(String definitionStatus) {
		this.definitionStatus = definitionStatus;
	}

	public List<Relationship> getRelationships() {
		return relationships;
	}

	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}
}
