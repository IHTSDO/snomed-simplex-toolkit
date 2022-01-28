package com.snomed.derivativemanagementtool.client.domain;

import java.util.ArrayList;
import java.util.List;

public class Concept {

	private String conceptId;
	private List<Description> descriptions;
	private List<Axiom> classAxioms;
	private List<Relationship> relationships;

	public Concept() {
		descriptions = new ArrayList<>();
		classAxioms = new ArrayList<>();
		relationships = new ArrayList<>();
	}

	public Concept addDescription(Description description) {
		descriptions.add(description);
		return this;
	}

	public Concept addAxiom(Axiom axiom) {
		classAxioms.add(axiom);
		return this;
	}

	public Concept addRelationship(Relationship relationship) {
		relationships.add(relationship);
		return this;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
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
