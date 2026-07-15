package org.snomed.simplex.translation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mixed batch prompt: context lines (English → target) and lines to translate.
 * {@link #translateLineNumbers} maps prompt line number to English term for response parsing.
 */
public record BatchTranslationPrompt(List<String> promptLines, Map<Integer, String> translateLineNumbers) {

	public BatchTranslationPrompt {
		promptLines = List.copyOf(promptLines);
		translateLineNumbers = Map.copyOf(translateLineNumbers);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private final List<String> lines = new java.util.ArrayList<>();
		private final Map<Integer, String> translateLineNumbers = new LinkedHashMap<>();
		private int lineNum = 1;

		public Builder addContextLine(String english, String target) {
			lines.add(lineNum + "|" + english + " → " + target);
			lineNum++;
			return this;
		}

		public Builder addTranslateLine(String english) {
			translateLineNumbers.put(lineNum, english);
			lines.add(lineNum + "|" + english);
			lineNum++;
			return this;
		}

		public BatchTranslationPrompt build() {
			return new BatchTranslationPrompt(lines, translateLineNumbers);
		}
	}
}
