package com.snomed.derivativemanagementtool.domain;

public class CodeSystem {

	private String name;
	private String shortName;
	private String branchPath;
	private Integer dependantVersionEffectiveTime;

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
}
