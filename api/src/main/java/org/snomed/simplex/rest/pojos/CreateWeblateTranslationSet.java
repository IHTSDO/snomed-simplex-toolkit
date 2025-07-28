package org.snomed.simplex.rest.pojos;

import org.snomed.simplex.weblate.domain.TranslationSubsetType;

public class CreateWeblateTranslationSet {

	private String name;
	private String label;
	private String ecl;
	private TranslationSubsetType subsetType;
	private String selectionCodesystem;

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

	public String getEcl() {
		return ecl;
	}

	public void setEcl(String ecl) {
		this.ecl = ecl;
	}

	public TranslationSubsetType getSubsetType() {
		return subsetType;
	}

	public void setSubsetType(TranslationSubsetType subsetType) {
		this.subsetType = subsetType;
	}

	public String getSelectionCodesystem() {
		return selectionCodesystem;
	}

	public void setSelectionCodesystem(String selectionCodesystem) {
		this.selectionCodesystem = selectionCodesystem;
	}

}
