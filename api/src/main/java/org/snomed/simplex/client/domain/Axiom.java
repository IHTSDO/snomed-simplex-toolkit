package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class Axiom extends Component {

	private String axiomId;
	private String definitionStatus;
	private List<Relationship> relationships;

	public Axiom() {
	}

	public Axiom(String definitionStatus, List<Relationship> relationships) {
		this.definitionStatus = definitionStatus;
		this.relationships = relationships;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return getAxiomId();
	}

	public String getAxiomId() {
		return axiomId;
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
