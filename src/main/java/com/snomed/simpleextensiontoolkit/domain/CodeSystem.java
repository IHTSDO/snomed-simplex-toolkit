package com.snomed.simpleextensiontoolkit.domain;

import com.snomed.simpleextensiontoolkit.client.ConceptMini;

import java.util.List;

public class CodeSystem {

	private String name;
	private String shortName;
	private String branchPath;
	private Integer dependantVersionEffectiveTime;
	private List<ConceptMini> modules;

	public String getName() {
		return name;
	}

	public String getShortName() {
		return shortName;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public Integer getDependantVersionEffectiveTime() {
		return dependantVersionEffectiveTime;
	}

	public List<ConceptMini> getModules() {
		return modules;
	}
}
