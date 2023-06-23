package com.snomed.simplextoolkit.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.snomed.simplextoolkit.exceptions.ServiceException;

import java.util.List;
import java.util.Objects;

public class CodeSystem {

	private String name;
	private String shortName;
	private String branchPath;
	private Integer dependantVersionEffectiveTime;
	private String defaultModule;
	private String defaultModuleDisplay;
	private List<ConceptMini> modules;
	private boolean postcoordinated;

	public CodeSystem() {
	}

	public CodeSystem(String name, String shortName, String branchPath) {
		this.name = name;
		this.shortName = shortName;
		this.branchPath = branchPath;
	}

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

	public boolean isPostcoordinated() {
		return postcoordinated;
	}

	@JsonIgnore
	public String getDefaultModuleOrThrow() throws ServiceException {
		if (defaultModule == null) {
			throw new ServiceException("No default module set for this code system.");
		}
		return defaultModule;
	}

	public String getDefaultModule() {
		return defaultModule;
	}

	public String getDefaultModuleDisplay() {
		return defaultModuleDisplay;
	}

	public void setDefaultModule(String defaultModule) {
		this.defaultModule = defaultModule;
	}

	public void setDefaultModuleDisplay(String defaultModuleDisplay) {
		this.defaultModuleDisplay = defaultModuleDisplay;
	}

	@Override
	public String toString() {
		return "CodeSystem{" +
				"shortName='" + shortName + '\'' +
				", branchPath='" + branchPath + '\'' +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CodeSystem that = (CodeSystem) o;
		return Objects.equals(shortName, that.shortName) && Objects.equals(branchPath, that.branchPath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(shortName, branchPath);
	}
}
