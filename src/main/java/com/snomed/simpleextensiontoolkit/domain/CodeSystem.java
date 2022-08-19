package com.snomed.simpleextensiontoolkit.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.snomed.simpleextensiontoolkit.client.ConceptMini;
import com.snomed.simpleextensiontoolkit.exceptions.ServiceException;

import java.util.List;

public class CodeSystem {

	private String name;
	private String shortName;
	private String branchPath;
	private Integer dependantVersionEffectiveTime;
	private String defaultModule;
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

	@JsonIgnore
	public String getDefaultModuleOrThrow() throws ServiceException {
		if (defaultModule == null) {
			throw new ServiceException("No default module set for this code system.");
		}
		return defaultModule;
	}

	public void setDefaultBranch(String defaultModule) {
		this.defaultModule = defaultModule;
	}
}
