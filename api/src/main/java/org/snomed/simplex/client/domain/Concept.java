package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.snomed.simplex.client.domain.Description.Type.FSN;

public class Concept extends Component {

	public static final String PRIMITIVE = "PRIMITIVE";

	private String conceptId;
	private DescriptionMini pt;
	private DescriptionMini fsn;
	private String definitionStatus;
	private List<Description> descriptions;
	private List<Axiom> classAxioms;
	private List<Axiom> gciAxioms;
	private List<Relationship> relationships;
	private List<AlternateIdentifier> alternateIdentifiers;
	private String inactivationIndicator;
	private Map<String, List<String>> associationTargets;

	@SuppressWarnings("unused")
	private Concept() {
		// For Jackson library
	}

	public Concept(String moduleId) {
		super(moduleId);
		definitionStatus = PRIMITIVE;
		descriptions = new ArrayList<>();
		classAxioms = new ArrayList<>();
		gciAxioms = new ArrayList<>();
		relationships = new ArrayList<>();
		alternateIdentifiers = new ArrayList<>();
	}

	public Concept addDescription(Description description) {
		if (description.getModuleId() == null) {
			description.setModuleId(getModuleId());
		}
		descriptions.add(description);
		return this;
	}

	public Concept addAxiom(Axiom axiom) {
		if (axiom.getModuleId() == null) {
			axiom.setModuleId(getModuleId());
		}
		classAxioms.add(axiom);
		return this;
	}

	public Concept addRelationship(Relationship relationship) {
		if (relationship.getModuleId() == null) {
			relationship.setModuleId(getModuleId());
		}
		relationships.add(relationship);
		return this;
	}

	@JsonIgnore
	public String getEnSemanticTag() {
		Optional<Description> enFSN = getEnFSN();
		String term = enFSN.orElse(new Description().setTerm("Finding (finding)")).getTerm();
		return term.substring(term.lastIndexOf("("));
	}

	private Optional<Description> getEnFSN() {
		return descriptions.stream().filter(description -> description.isActive() && description.getType() == FSN &&
				description.getLang().equals("en")).findFirst();
	}

	public boolean isDefined() {
		return !definitionStatus.equals(PRIMITIVE);
	}

	@Override
	@JsonIgnore
	public String getId() {
		return getConceptId();
	}

	public String getConceptId() {
		return conceptId;
	}

	@JsonIgnore
	public Long getConceptIdAsLong() {
		return Long.parseLong(conceptId);
	}

	public Concept setConceptId(String conceptId) {
		this.conceptId = conceptId;
		return this;
	}

	public DescriptionMini getPt() {
		return pt;
	}

	public void setPt(DescriptionMini pt) {
		this.pt = pt;
	}

	public DescriptionMini getFsn() {
		return fsn;
	}

	public void setFsn(DescriptionMini fsn) {
		this.fsn = fsn;
	}

	public String getDefinitionStatus() {
		return definitionStatus;
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

	public List<Axiom> getGciAxioms() {
		return gciAxioms;
	}

	public List<Relationship> getRelationships() {
		return relationships;
	}

	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}

	public List<AlternateIdentifier> getAlternateIdentifiers() {
		return alternateIdentifiers;
	}

	public String getInactivationIndicator() {
		return inactivationIndicator;
	}

	public void setInactivationIndicator(String inactivationIndicator) {
		this.inactivationIndicator = inactivationIndicator;
	}

	public Map<String, List<String>> getAssociationTargets() {
		return associationTargets;
	}

	public void setAssociationTargets(Map<String, List<String>> associationTargets) {
		this.associationTargets = associationTargets;
	}
}
