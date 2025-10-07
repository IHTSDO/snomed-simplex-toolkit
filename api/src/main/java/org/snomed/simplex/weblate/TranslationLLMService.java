package org.snomed.simplex.weblate;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private final Logger logger = LoggerFactory.getLogger(TranslationLLMService.class);

	public TranslationLLMService(LLMService llmService) {
		this.llmService = llmService;
	}

	public Map<String, List<String>> suggestTranslations(WeblateTranslationSet translationSet, List<String> englishTerm, boolean multipleSuggestions, boolean fast) {
		String languageCode = translationSet.getLanguageCode();
		String systemAdvice = "Translate the following clinical terminology terms from English to %s.".formatted(languageCode);
		String languageAdvice = translationSet.getAiLanguageAdvice();
		Map<String, String> aiGoldenSet = translationSet.getAiGoldenSet();
		String languageAdviceFormatted = "";
		if (Strings.isNotEmpty(languageAdvice)) {
			languageAdviceFormatted = "%s".formatted(languageAdvice);
		}
		String responseFormat = "For each term provided, return the term number and the %s translation.\n".formatted(languageCode) +
			"Use the exact formatting below:\n" +
			"<term number>|<translation>";
		if (multipleSuggestions) {
			responseFormat = "For each term provided, return the term number and the top two %s translations.\n".formatted(languageCode) +
				"Use the exact formatting below:\n" +
				"<term number>|<translation1>|<translation2>";
		}
		String guidelines = getGuidelines(multipleSuggestions);

		// System Advice
		// Language Advice
		// Term
		// Format
		StringBuilder englishTerms = new StringBuilder();
		int lineNum = 1;
		for (String term : englishTerm) {
			englishTerms.append(lineNum++).append("|").append(term).append("\n");
		}
		String response = llmService.chat((
				"""
					%s
					%s
					%s
					%s
					Examples:
					%s
					English terms:
					%s
					"""
			).formatted(systemAdvice, responseFormat, guidelines, languageAdviceFormatted, formatGoldenExamples(aiGoldenSet), englishTerms),
			fast
		);

		return processResponse(englishTerm, response);
	}

	private String formatGoldenExamples(Map<String, String> aiGoldenSet) {
		StringBuilder builder = new StringBuilder();
		if (aiGoldenSet == null) {
			return builder.toString();
		}
		int lineNum = 1;
		for (Map.Entry<String, String> entry : aiGoldenSet.entrySet()) {
			String key = entry.getKey();
			if (!key.contains("|")) {
				continue;
			}
			key = key.split("\\|")[1];
			builder.append(lineNum++).append("|").append(key).append(" → ").append(entry.getValue()).append("\n");
		}
		return builder.toString();
	}

	private @NotNull Map<String, List<String>> processResponse(List<String> englishTerm, String response) {
		Map<String, List<String>> allSuggestions = new LinkedHashMap<>();
		for (String line : response.split("\n")) {
			if (line.contains("|")) {
				String[] split = line.split("\\|");
				String termNum = split[0].trim();
				if (!termNum.matches("\\d*")) {
					logger.error("Invalid term number: {} in response:{}", termNum, response);
					continue;
				}
				List<String> suggestions = new ArrayList<>();
				for (int i = 1; i < split.length; i++) {
					String trimmed = split[i].trim();
					if (!trimmed.isEmpty()) {
						suggestions.add(trimmed);
					}
				}
				String en = englishTerm.get(Integer.parseInt(termNum) - 1);
				allSuggestions.put(en, suggestions);
			}
		}
		return allSuggestions;
	}

	private static @NotNull String getGuidelines(boolean multipleSuggestions) {
		String guidelines =
			"""
				Guidelines:
				- Provide X_TRANSLATIONS after each line number. If a translation cannot be found output the line number and pipe but leave the translation blank.
				- Preserve the original order of the lines; do not reorder, group, or summarize them.
				- Preserve all modifiers, qualifiers, any body location descriptors.
				- Set reasoning_effort = minimal; outputs should be terse, limited to the requested direct translations in plain text.""";

		if (multipleSuggestions) {
			guidelines = guidelines.replace("X_TRANSLATIONS", "two translations");
		} else {
			guidelines = guidelines.replace("X_TRANSLATIONS", "one translation");
		}
		return guidelines;
	}
}
