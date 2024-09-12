package org.snomed.simplex.domain.activity;

public enum ActivityType {

	CREATE("Create"), UPDATE("Update"), DELETE("Delete"),
	START_RELEASE_PREP("Start release prep"),
	STOP_RELEASE_PREP("Stop release prep"),
	UPGRADE("Upgrade"), CLASSIFY("Classify"), VALIDATE("Validate"), AUTOMATIC_FIX("Automatic Fix"),
	UPDATE_CONFIGURATION("Update configuration"), START_BUILD("Start build");

	private final String display;

	ActivityType(String display) {
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}
}
