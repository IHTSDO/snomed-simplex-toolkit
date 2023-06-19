package com.snomed.simplextoolkit.client.domain;

import java.util.ArrayList;
import java.util.List;

public class Concept {

	private String conceptId;
	private String moduleId;
	private List<Description> descriptions;
	private List<Axiom> classAxioms;
	private List<Relationship> relationships;

	@SuppressWarnings("unused")
	private Concept() {
		// For Jackson library
	}

	public Concept(String moduleId) {
		this.moduleId = moduleId;
		descriptions = new ArrayList<>();
		classAxioms = new ArrayList<>();
		relationships = new ArrayList<>();
	}

	public Concept addDescription(Description description) {
		if (description.getModuleId() == null) {
			description.setModuleId(moduleId);
		}
		descriptions.add(description);
		return this;
	}

	public Concept addAxiom(Axiom axiom) {
		if (axiom.getModuleId() == null) {
			axiom.setModuleId(moduleId);
		}
		classAxioms.add(axiom);
		return this;
	}

	public Concept addRelationship(Relationship relationship) {
		if (relationship.getModuleId() == null) {
			relationship.setModuleId(moduleId);
		}
		relationships.add(relationship);
		return this;
	}

	public String getConceptId() {
		return conceptId;
	}

	public Concept setConceptId(String conceptId) {
		this.conceptId = conceptId;
		return this;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public List<Description> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(List<Description> descriptions) {
		this.descriptions = descriptions;
	}

	public List<Axiom> getClassAxioms() {
		return classAxioms;
	}

	public void setClassAxioms(List<Axiom> classAxioms) {
		this.classAxioms = classAxioms;
	}

	public List<Relationship> getRelationships() {
		return relationships;
	}

	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}
}
