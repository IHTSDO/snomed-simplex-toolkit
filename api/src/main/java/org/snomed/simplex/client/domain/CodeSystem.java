package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.simplex.exceptions.ServiceException;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CodeSystem {

	private String name;
	private String shortName;
	private String branchPath;
	private String simplexWorkingBranch;

	private CodeSystemClassificationStatus classificationStatus;
	private CodeSystemValidationStatus validationStatus;
	private boolean showCustomConcepts;
	private boolean classified;
	private boolean preparingRelease;
	private String latestValidationReport;
	private long contentHeadTimestamp;
	private Set<String> userRoles;

	private Integer dependantVersionEffectiveTime;
	private String dependencyPackage;
	private String namespace;
	private String defaultModule;
	private String defaultModuleDisplay;
	private boolean postcoordinated;
	private boolean dailyBuildAvailable;
	private Map<String, String> languages;
	private List<ConceptMini> modules;
	private CodeSystemVersion latestVersion;

	private Branch branchObject;

	public record CodeSystemVersion(int effectiveDate, String branchPath) {}

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

	public CodeSystemClassificationStatus getClassificationStatus() {
		return classificationStatus;
	}

	public void setClassificationStatus(CodeSystemClassificationStatus classificationStatus) {
		this.classificationStatus = classificationStatus;
	}

	public CodeSystemValidationStatus getValidationStatus() {
		return validationStatus;
	}

	public void setValidationStatus(CodeSystemValidationStatus validationStatus) {
		this.validationStatus = validationStatus;
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

	public Set<String> getUserRoles() {
		return userRoles;
	}

	public void setClassified(boolean classified) {
		this.classified = classified;
	}

	public boolean isClassified() {
		return classified;
	}

	public CodeSystemVersion getLatestVersion() {
		return latestVersion;
	}

	public void setPreparingRelease(boolean preparingRelease) {
		this.preparingRelease = preparingRelease;
	}

	public boolean isPreparingRelease() {
		return preparingRelease;
	}

	@JsonIgnore
	public Branch getBranchObject() {
		return branchObject;
	}

	public void setBranchObject(Branch branchObject) {
		this.branchObject = branchObject;
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
