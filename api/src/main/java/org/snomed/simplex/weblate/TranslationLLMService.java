package org.snomed.simplex.weblate;

import org.apache.logging.log4j.util.Strings;
import org.snomed.simplex.ai.LLMService;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TranslationLLMService {

	private final LLMService llmService;

	public TranslationLLMService(LLMService llmService) {
		this.llmService = llmService;
	}

	public Map<String, List<String>> suggestTranslations(WeblateTranslationSet translationSet, List<String> englishTerm, boolean multipleSuggestions, boolean fast) {

		Map<String, List<String>> allSuggestions = new LinkedHashMap<>();

		String languageCode = translationSet.getLanguageCode();
		String systemAdvice = "Translate the following clinical terminology terms from English to %s.".formatted(languageCode);
		String languageAdvice = translationSet.getAiLanguageAdvice();
		String languageAdviceFormatted = "";
		if (Strings.isNotEmpty(languageAdvice)) {
			languageAdviceFormatted = "%s\n".formatted(languageAdvice);
		}
		String responseFormat = "For each term provided, return the English term and the %s translation.\n".formatted(languageCode) +
			"Use the exact formatting below:\n" +
			"<english term>|<translation>";
		if (multipleSuggestions) {
			responseFormat = "For each term provided, return the English term and the top two %s translations.\n".formatted(languageCode) +
				"Use the exact formatting below:\n" +
				"<english term>|<translation1>|<translation2>";
		}
		String guidelines =
			"""
				Guidelines:
				- Provide X_TRANSLATIONS after each English term. If a translation cannot be found, leave that translation blank after the '|'.
				- Preserve the original order of the English terms; do not reorder, group, or summarize them.
				- Preserve all modifiers, qualifiers, any body location descriptors.
				- Set reasoning_effort = minimal; outputs should be terse, limited to the requested direct translations in plain text.
				""";

		if (multipleSuggestions) {
			guidelines = guidelines.replace("X_TRANSLATIONS", "two translations");
		} else {
			guidelines = guidelines.replace("X_TRANSLATIONS", "one translation");
		}

		// System Advice
		// Language Advice
		// Term
		// Format
		String response = llmService.chat((
				"""
					%s
					%s
					%s
					%s
					English terms:
					%s
					"""
			).formatted(systemAdvice, responseFormat, guidelines, languageAdviceFormatted, String.join("\n", englishTerm)),
			fast
		);
		for (String line : response.split("\n")) {
			if (line.contains("|")) {
				String[] split = line.split("\\|");
				String en = split[0].trim();
				List<String> suggestions = new ArrayList<>();
				for (int i = 1; i < split.length; i++) {
					String trimmed = split[i].trim();
					if (!trimmed.isEmpty()) {
						suggestions.add(trimmed);
					}
				}
				allSuggestions.put(en, suggestions);
			}
		}
		return allSuggestions;
	}
}
