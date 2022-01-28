package com.snomed.derivativemanagementtool.client.domain;

public class Relationship {

	private String typeId;
	private String destinationId;
	private String modifier;
	private String characteristicType;

	public static Relationship stated(String typeId, String destinationId) {
		Relationship relationship = new Relationship();
		relationship.typeId = typeId;
		relationship.destinationId = destinationId;
		relationship.characteristicType = "STATED_RELATIONSHIP";
		return relationship;
	}

	public static Relationship inferred(String typeId, String destinationId) {
		Relationship relationship = new Relationship();
		relationship.typeId = typeId;
		relationship.destinationId = destinationId;
		relationship.characteristicType = "INFERRED_RELATIONSHIP";
		relationship.modifier = "EXISTENTIAL";
		return relationship;
	}

	public Relationship() {
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
}
