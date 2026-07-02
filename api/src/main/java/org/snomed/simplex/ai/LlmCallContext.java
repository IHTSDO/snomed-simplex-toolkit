package org.snomed.simplex.ai;

public record LlmCallContext(String codesystem, int conceptsTranslated) {

	public LlmCallContext(String codesystem) {
		this(codesystem, 0);
	}
}
