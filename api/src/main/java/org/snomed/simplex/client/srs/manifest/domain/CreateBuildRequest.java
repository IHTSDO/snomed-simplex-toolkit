package org.snomed.simplex.client.srs.manifest.domain;

public class CreateBuildRequest {

	private String effectiveDate;
	private String exportCategory;
	private String branchPath;
	private boolean loadTermServerData;
	private boolean loadExternalRefsetData;
	private boolean enableTraceabilityValidation;
	private boolean replaceExistingEffectiveTime;

	public CreateBuildRequest(String effectiveDate, String branchPath) {
		this.effectiveDate = effectiveDate;
		this.branchPath = branchPath;
		exportCategory = "UNPUBLISHED";
		replaceExistingEffectiveTime = true;
	}

	public String getEffectiveDate() {
		return effectiveDate;
	}

	public String getExportCategory() {
		return exportCategory;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public boolean isLoadTermServerData() {
		return loadTermServerData;
	}

	public boolean isLoadExternalRefsetData() {
		return loadExternalRefsetData;
	}

	public boolean isEnableTraceabilityValidation() {
		return enableTraceabilityValidation;
	}

	public boolean isReplaceExistingEffectiveTime() {
		return replaceExistingEffectiveTime;
	}
}
