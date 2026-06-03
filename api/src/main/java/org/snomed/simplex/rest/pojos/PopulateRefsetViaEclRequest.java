package org.snomed.simplex.rest.pojos;

public class PopulateRefsetViaEclRequest {

	private String ecl;
	private String selectionCodesystem;

	public String getEcl() {
		return ecl;
	}

	public void setEcl(String ecl) {
		this.ecl = ecl;
	}

	public String getSelectionCodesystem() {
		return selectionCodesystem;
	}

	public void setSelectionCodesystem(String selectionCodesystem) {
		this.selectionCodesystem = selectionCodesystem;
	}

}
