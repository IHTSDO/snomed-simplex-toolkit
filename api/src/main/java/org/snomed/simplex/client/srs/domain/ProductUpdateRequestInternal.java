package org.snomed.simplex.client.srs.domain;

import com.fasterxml.jackson.annotation.JsonGetter;

public class ProductUpdateRequestInternal {

	// buildConfiguration
	private boolean firstTimeRelease;
	private String readmeHeader;
	private String readmeEndDate;
	private String licenseStatement;

	// buildConfiguration.extensionConfig
	private String namespaceId;
	private String defaultModuleId;
	private String moduleIds;
	private String previousPublishedPackage;
	private String extensionDependencyRelease;
	private String previousEditionDependencyEffectiveDate;
	private boolean releaseAsAnEdition;
	private boolean classifyOutputFiles;

	// qaTestConfig
	private String assertionGroupNames;
	private boolean enableDrools;
	private String droolsRulesGroupNames;
	private boolean enableMRCMValidation;

	public String getReadmeHeader() {
		return readmeHeader;
	}

	public void setReadmeHeader(String readmeHeader) {
		this.readmeHeader = readmeHeader;
	}

	public String getReadmeEndDate() {
		return readmeEndDate;
	}

	public void setReadmeEndDate(String readmeEndDate) {
		this.readmeEndDate = readmeEndDate;
	}

	public String getLicenseStatement() {
		return licenseStatement;
	}

	public void setLicenseStatement(String licenseStatement) {
		this.licenseStatement = licenseStatement;
	}

	public String getAssertionGroupNames() {
		return assertionGroupNames;
	}

	public void setAssertionGroupNames(String assertionGroupNames) {
		this.assertionGroupNames = assertionGroupNames;
	}

	public void setEnableDrools(boolean enableDrools) {
		this.enableDrools = enableDrools;
	}

	public boolean isEnableDrools() {
		return enableDrools;
	}

	public void setDroolsRulesGroupNames(String droolsRulesGroupNames) {
		this.droolsRulesGroupNames = droolsRulesGroupNames;
	}

	public String getDroolsRulesGroupNames() {
		return droolsRulesGroupNames;
	}

	public void setEnableMRCMValidation(boolean enableMRCMValidation) {
		this.enableMRCMValidation = enableMRCMValidation;
	}

	public boolean isEnableMRCMValidation() {
		return enableMRCMValidation;
	}

	public void setNamespaceId(String namespaceId) {
		this.namespaceId = namespaceId;
	}

	public String getNamespaceId() {
		return namespaceId;
	}

	public void setDefaultModuleId(String defaultModuleId) {
		this.defaultModuleId = defaultModuleId;
	}

	public String getDefaultModuleId() {
		return defaultModuleId;
	}

	public void setModuleIds(String moduleIds) {
		this.moduleIds = moduleIds;
	}

	public String getModuleIds() {
		return moduleIds;
	}

	public void setExtensionDependencyRelease(String extensionDependencyRelease) {
		this.extensionDependencyRelease = extensionDependencyRelease;
	}

	public String getExtensionDependencyRelease() {
		return extensionDependencyRelease;
	}

	public String getPreviousEditionDependencyEffectiveDate() {
		return previousEditionDependencyEffectiveDate;
	}

	public void setPreviousEditionDependencyEffectiveDate(String previousEditionDependencyEffectiveDate) {
		this.previousEditionDependencyEffectiveDate = previousEditionDependencyEffectiveDate;
	}

	public void setReleaseAsAnEdition(boolean releaseAsAnEdition) {
		this.releaseAsAnEdition = releaseAsAnEdition;
	}

	@JsonGetter("releaseExtensionAsAnEdition")
	public boolean isReleaseAsAnEdition() {
		return releaseAsAnEdition;
	}

	public void setFirstTimeRelease(boolean firstTimeRelease) {
		this.firstTimeRelease = firstTimeRelease;
	}

	public boolean isFirstTimeRelease() {
		return firstTimeRelease;
	}

	public boolean isClassifyOutputFiles() {
		return classifyOutputFiles;
	}

	public void setClassifyOutputFiles(boolean classifyOutputFiles) {
		this.classifyOutputFiles = classifyOutputFiles;
	}

	public void setPreviousPublishedPackage(String previousPublishedPackage) {
		this.previousPublishedPackage = previousPublishedPackage;
	}

	public String getPreviousPublishedPackage() {
		return previousPublishedPackage;
	}
}
