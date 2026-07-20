package org.snomed.simplex.translation;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.ai.LLMService;
import org.snomed.simplex.ai.LlmCallContext;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.service.LanguageTranslationPolicyService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TranslationLLMService {

	public static final String BATCH_GUIDELINES = """
		Guidelines:
		- Return one translation only for lines that contain English text without an existing "→ translation".
		- Do not return translations for lines that already show "English → translation".
		- If a translation cannot be found output the line number and pipe but leave the translation blank.
		- Preserve the original order of the lines; do not reorder, group, or summarize them.
		- Preserve all modifiers, qualifiers, any body location descriptors.
		- Set reasoning_effort = minimal; outputs should be terse, limited to the requested direct translations in plain text.""";

	private final LLMService llmService;
	private final LanguageTranslationPolicyService languageTranslationPolicyService;
	private final LanguagePolicyPromptFormatter languagePolicyPromptFormatter;
	private final Logger logger = LoggerFactory.getLogger(TranslationLLMService.class);

	public TranslationLLMService(LLMService llmService, LanguageTranslationPolicyService languageTranslationPolicyService,
			LanguagePolicyPromptFormatter languagePolicyPromptFormatter) {
		this.llmService = llmService;
		this.languageTranslationPolicyService = languageTranslationPolicyService;
		this.languagePolicyPromptFormatter = languagePolicyPromptFormatter;
	}

	public Map<String, List<String>> suggestTranslations(SnolateTranslationSet translationSet, List<String> englishTerm, boolean multipleSuggestions, boolean fast) {
		LanguageTranslationPolicy policy = languageTranslationPolicyService
				.findByCodeSystemAndRefset(translationSet.getCodesystem(), translationSet.getRefset())
				.orElse(null);
		String languagePolicyText = languagePolicyPromptFormatter.format(policy);
		return suggestTranslations(translationSet.getLanguageCode(), languagePolicyText, translationSet.getAiGoldenSet(),
				englishTerm, multipleSuggestions, fast, new LlmCallContext(translationSet.getCodesystem(), englishTerm.size()));
	}

	public Map<String, List<String>> suggestBatchTranslations(SnolateTranslationSet translationSet, BatchTranslationPrompt prompt) {
		LanguageTranslationPolicy policy = languageTranslationPolicyService
				.findByCodeSystemAndRefset(translationSet.getCodesystem(), translationSet.getRefset())
				.orElse(null);
		String languagePolicyText = languagePolicyPromptFormatter.format(policy);
		return suggestBatchTranslations(translationSet.getLanguageCode(), languagePolicyText, translationSet.getAiGoldenSet(),
				prompt, new LlmCallContext(translationSet.getCodesystem(), prompt.translateLineNumbers().size()));
	}

	public Map<String, List<String>> suggestBatchTranslations(String languageCode, String languagePolicyText, Map<String, String> aiGoldenSet,
			BatchTranslationPrompt prompt, LlmCallContext context) {
		String systemAdvice = "Translate the following clinical terminology terms from English to %s.".formatted(languageCode);
		String languageAdviceFormatted = "";
		if (Strings.isNotEmpty(languagePolicyText)) {
			languageAdviceFormatted = "%s".formatted(languagePolicyText);
		}
		String responseFormat = """
				For each line that needs translation, return the line number and the %s translation.
				Lines that already show "English → translation" are completed examples; do not return translations for those lines.
				Use the exact formatting below:
				<line number>|<translation>""".formatted(languageCode);

		StringBuilder englishTerms = new StringBuilder();
		for (String line : prompt.promptLines()) {
			englishTerms.append(line).append("\n");
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
			).formatted(systemAdvice, responseFormat, BATCH_GUIDELINES, languageAdviceFormatted, formatGoldenExamples(aiGoldenSet), englishTerms),
				false,
				context
		);

		return processBatchResponse(prompt.translateLineNumbers(), response);
	}

	public Map<String, List<String>> suggestTranslations(String languageCode, String languagePolicyText, Map<String, String> aiGoldenSet,
			List<String> englishTerm, boolean multipleSuggestions, boolean fast, LlmCallContext context) {
		String systemAdvice = "Translate the following clinical terminology terms from English to %s.".formatted(languageCode);
		String languageAdviceFormatted = "";
		if (Strings.isNotEmpty(languagePolicyText)) {
			languageAdviceFormatted = "%s".formatted(languagePolicyText);
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
				fast,
				context
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

	private Map<String, List<String>> processBatchResponse(Map<Integer, String> translateLineNumbers, String response) {
		Map<String, List<String>> allSuggestions = new LinkedHashMap<>();
		for (String line : response.split("\n")) {
			if (line.contains("|")) {
				String[] split = line.split("\\|");
				String termNum = split[0].trim();
				if (!termNum.matches("\\d*")) {
					logger.error("Invalid term number: {} in response:{}", termNum, response);
					continue;
				}
				String english = translateLineNumbers.get(Integer.parseInt(termNum));
				if (english == null) {
					continue;
				}
				List<String> suggestions = new ArrayList<>();
				for (int i = 1; i < split.length; i++) {
					String trimmed = split[i].trim();
					if (!trimmed.isEmpty()) {
						suggestions.add(trimmed);
					}
				}
				allSuggestions.put(english, suggestions);
			}
		}
		return allSuggestions;
	}

	private Map<String, List<String>> processResponse(List<String> englishTerm, String response) {
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

	private static String getGuidelines(boolean multipleSuggestions) {
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
