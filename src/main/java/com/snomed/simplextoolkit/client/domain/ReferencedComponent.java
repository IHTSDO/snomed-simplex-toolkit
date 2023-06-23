package com.snomed.simplextoolkit.client.domain;

public class ReferencedComponent {

	private String conceptId;
	private boolean active;
	private DescriptionMini fsn;
	private DescriptionMini pt;
	private String moduleId;
	private String lang;

	public String getConceptId() {
		return conceptId;
	}

	public boolean isActive() {
		return active;
	}

	public DescriptionMini getFsn() {
		return fsn;
	}

	public DescriptionMini getPt() {
		return pt;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getLang() {
		return lang;
	}
}
