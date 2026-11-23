package org.snomed.simplex.rest.pojos;

public class CreateSnolateTranslationSetFromStandardCodeList {

	private String name;
	private String label;
	private String description;
	private String standardCodeListId;
	private String valueSetName;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getStandardCodeListId() {
		return standardCodeListId;
	}

	public void setStandardCodeListId(String standardCodeListId) {
		this.standardCodeListId = standardCodeListId;
	}

	public String getValueSetName() {
		return valueSetName;
	}

	public void setValueSetName(String valueSetName) {
		this.valueSetName = valueSetName;
	}
}
