package org.snomed.simplex.domain.activity;

public enum ActivityType {

	CREATE("Create"), UPDATE("Update"), DELETE("Delete"),
	START_RELEASE_PREP("Start release prep"),
	START_AUTHORING("Start authoring"),
	APPROVE_CONTENT("Approve content"),
	START_MAINTENANCE("Start maintenance"),
	UPGRADE("Upgrade"), CLASSIFY("Classify"), VALIDATE("Validate"), AUTOMATIC_FIX("Automatic Fix"),
	UPDATE_CONFIGURATION("Update configuration"), BUILD_RELEASE("Build release"), FINALIZE_RELEASE("Finalize release"),

	WEBLATE_LANGUAGE_INITIALISATION("Initialise Translation Tool language"),
	TRANSLATION_SET_CREATE("Create translation set");

	private final String display;

	ActivityType(String display) {
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}

}
