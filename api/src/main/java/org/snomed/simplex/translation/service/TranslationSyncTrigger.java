package org.snomed.simplex.translation.service;

/** Why translation content was synchronised from Snowstorm. The name is sent to browsers in the {@code translation-sync} event. */
public enum TranslationSyncTrigger {

	TASK_PROMOTED("task promoted"),
	PROJECT_REBASED("project rebase");

	private final String label;

	TranslationSyncTrigger(String label) {
		this.label = label;
	}

	/** Short human-readable description, used in the jobs log. */
	public String getLabel() {
		return label;
	}
}
