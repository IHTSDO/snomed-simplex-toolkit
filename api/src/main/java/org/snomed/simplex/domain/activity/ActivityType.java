package org.snomed.simplex.domain.activity;

public enum ActivityType {

	CREATE("Create"), UPDATE("Update"), DELETE("Delete"),
	START_RELEASE_PREP("Start release prep"),
	START_AUTHORING("Start authoring"),
	APPROVE_CONTENT("Approve content"),
	START_MAINTENANCE("Start maintenance"),
	UPGRADE("Upgrade"), CLASSIFY("Classify"), VALIDATE("Validate"), AUTOMATIC_FIX("Automatic Fix"),
	UPDATE_CONFIGURATION("Update configuration"), BUILD_RELEASE("Build release"), FINALIZE_RELEASE("Finalize release");

	private final String display;

	ActivityType(String display) {
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}

}
