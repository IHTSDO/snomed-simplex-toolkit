package org.snomed.simplex.domain.activity;

public enum ActivityType {

	CREATE("Create"), UPDATE("Update"), DELETE("Delete"),
	START_RELEASE_PREP("Start release prep"),
	START_AUTHORING("Start release prep"),
	ADD_CONTENT_APPROVAL("Add content approval"),
	REMOVE_CONTENT_APPROVAL("Remove content approval"),
	UPGRADE("Upgrade"), CLASSIFY("Classify"), VALIDATE("Validate"), AUTOMATIC_FIX("Automatic Fix"),
	UPDATE_CONFIGURATION("Update configuration"), BUILD_RELEASE("Build release");

	private final String display;

	ActivityType(String display) {
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}

}
