package com.snomed.simplextoolkit.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CodeSystem {

	private String name;
	private String shortName;
	private String branchPath;
	private String simplexWorkingBranch;
	private Integer dependantVersionEffectiveTime;
	private String dependencyPackage;
	private String namespace;
	private String defaultModule;
	private String defaultModuleDisplay;
	private boolean postcoordinated;
	private boolean dailyBuildAvailable;
	private boolean classified;
	private boolean showCustomConcepts;
	private String latestValidationReport;
	private long contentHeadTimestamp;
	private Map<String, String> languages;
	private List<ConceptMini> modules;

	public CodeSystem() {
	}

	public CodeSystem(String name, String shortName, String branchPath) {
		this.name = name;
		this.shortName = shortName;
		this.branchPath = branchPath;
	}

	public String getWorkingBranchPath() {
		return Strings.isBlank(simplexWorkingBranch) ? branchPath : simplexWorkingBranch;
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

	public String getSimplexWorkingBranch() {
		return simplexWorkingBranch;
	}

	public void setSimplexWorkingBranch(String simplexWorkingBranch) {
		this.simplexWorkingBranch = simplexWorkingBranch;
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

	public boolean isDailyBuildAvailable() {
		return dailyBuildAvailable;
	}

	public CodeSystem setDailyBuildAvailable(boolean dailyBuildAvailable) {
		this.dailyBuildAvailable = dailyBuildAvailable;
		return this;
	}

	@JsonIgnore
	public String getDefaultModuleOrThrow() throws ServiceException {
		if (defaultModule == null) {
			throw new ServiceException("No default module set for this code system.");
		}
		return defaultModule;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
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

	public Map<String, String> getLanguages() {
		return languages;
	}

	public void setLanguages(Map<String, String> languages) {
		this.languages = languages;
	}

	public boolean isClassified() {
		return classified;
	}

	public void setClassified(boolean classified) {
		this.classified = classified;
	}

	public boolean isShowCustomConcepts() {
		return showCustomConcepts;
	}

	public void setShowCustomConcepts(boolean showCustomConcepts) {
		this.showCustomConcepts = showCustomConcepts;
	}

	public String getDependencyPackage() {
		return dependencyPackage;
	}

	public void setDependencyPackage(String dependencyPackage) {
		this.dependencyPackage = dependencyPackage;
	}

	public void setLatestValidationReport(String latestValidationReport) {
		this.latestValidationReport = latestValidationReport;
	}

	public String getLatestValidationReport() {
		return latestValidationReport;
	}

	public long getContentHeadTimestamp() {
		return contentHeadTimestamp;
	}

	public void setContentHeadTimestamp(long contentHeadTimestamp) {
		this.contentHeadTimestamp = contentHeadTimestamp;
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
