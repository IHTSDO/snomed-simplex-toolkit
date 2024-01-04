package org.snomed.simplex.client.domain;

public class Relationship extends Component {

	private String relationshipId;
	private String sourceId;
	private String typeId;
	private String destinationId;
	private ConcreteValue concreteValue;
	private String modifier;
	private int groupId;
	private String characteristicType;
	private ConceptMini target;

	public static Relationship stated(String typeId, String destinationId, int group) {
		Relationship relationship = new Relationship();
		relationship.typeId = typeId;
		relationship.destinationId = destinationId;
		relationship.groupId = group;
		relationship.characteristicType = "STATED_RELATIONSHIP";
		return relationship;
	}

	public static Relationship inferred(String typeId, String destinationId, int group) {
		Relationship relationship = new Relationship();
		relationship.typeId = typeId;
		relationship.destinationId = destinationId;
		relationship.groupId = group;
		relationship.characteristicType = "INFERRED_RELATIONSHIP";
		relationship.modifier = "EXISTENTIAL";
		return relationship;
	}

	public String getRelationshipId() {
		return relationshipId;
	}

	public String getSourceId() {
		return sourceId;
	}

	public int getGroupId() {
		return groupId;
	}

	public String getTypeId() {
		return typeId;
	}

	public void setTypeId(String typeId) {
		this.typeId = typeId;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public void setDestinationId(String destinationId) {
		this.destinationId = destinationId;
	}

	public ConcreteValue getConcreteValue() {
		return concreteValue;
	}

	public String getModifier() {
		return modifier;
	}

	public void setModifier(String modifier) {
		this.modifier = modifier;
	}

	public String getCharacteristicType() {
		return characteristicType;
	}

	public void setCharacteristicType(String characteristicType) {
		this.characteristicType = characteristicType;
	}

	public ConceptMini getTarget() {
		return target;
	}

	public void setTarget(ConceptMini target) {
		this.target = target;
	}
}
