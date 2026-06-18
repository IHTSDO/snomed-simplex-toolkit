package org.snomed.simplex.translation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.snomed.simplex.snolate.domain.LanguagePolicyOption;
import org.snomed.simplex.snolate.domain.LanguagePolicyQuestion;
import org.snomed.simplex.snolate.domain.LanguagePolicyQuestionnaire;
import org.snomed.simplex.snolate.domain.LanguagePolicySection;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.service.LanguagePolicyQuestionnaireService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LanguagePolicyPromptFormatter {

	private final LanguagePolicyQuestionnaireService questionnaireService;
	private final ObjectMapper objectMapper;

	public LanguagePolicyPromptFormatter(LanguagePolicyQuestionnaireService questionnaireService, ObjectMapper objectMapper) {
		this.questionnaireService = questionnaireService;
		this.objectMapper = objectMapper;
	}

	public String format(LanguageTranslationPolicy policy) {
		if (policy == null || policy.getPolicyItems() == null || policy.getPolicyItems().isEmpty()) {
			return "";
		}
		LanguagePolicyQuestionnaire questionnaire = questionnaireService.getQuestionnaire();
		if (!questionnaire.version().equals(policy.getQuestionnaireVersion())) {
			return formatFallback(policy.getPolicyItems());
		}

		List<String> lines = new ArrayList<>();
		Map<String, String> items = policy.getPolicyItems();
		for (LanguagePolicySection section : questionnaire.sections()) {
			for (LanguagePolicyQuestion question : section.questions()) {
				appendQuestionLines(lines, question, items);
			}
		}
		if (lines.isEmpty()) {
			return "";
		}
		return "Language policy:\n" + String.join("\n", lines);
	}

	private void appendQuestionLines(List<String> lines, LanguagePolicyQuestion question, Map<String, String> items) {
		String answer = items.get(question.id());
		if (Strings.isNullOrEmpty(answer)) {
			return;
		}
		switch (question.type()) {
			case "choice", "boolean" -> appendChoiceLine(lines, question, answer, items);
			case "text" -> appendTextLine(lines, question, answer);
			case "keyValueTable" -> appendKeyValueTableLines(lines, answer);
			default -> { /* ignore unknown types */ }
		}
	}

	private void appendChoiceLine(List<String> lines, LanguagePolicyQuestion question, String answer, Map<String, String> items) {
		if (question.options() == null) {
			return;
		}
		Optional<LanguagePolicyOption> selected = question.options().stream().filter(o -> o.value().equals(answer)).findFirst();
		if (selected.isEmpty()) {
			return;
		}
		LanguagePolicyOption option = selected.get();
		if (!Strings.isNullOrEmpty(option.prompt())) {
			lines.add(option.prompt());
			return;
		}
		if (!Strings.isNullOrEmpty(option.promptTemplate()) && Boolean.TRUE.equals(option.allowFreeText())) {
			String freeText = items.getOrDefault(option.freeTextKey(), "");
			if (!Strings.isNullOrEmpty(freeText)) {
				lines.add(option.promptTemplate().replace("{freeText}", freeText));
			}
		}
	}

	private void appendTextLine(List<String> lines, LanguagePolicyQuestion question, String answer) {
		if (!Strings.isNullOrEmpty(question.promptTemplate())) {
			lines.add(question.promptTemplate().replace("{value}", answer));
		} else {
			lines.add("- %s: %s".formatted(question.title(), answer));
		}
	}

	private void appendKeyValueTableLines(List<String> lines, String answer) {
		try {
			Map<String, String> table = objectMapper.readValue(answer, new TypeReference<LinkedHashMap<String, String>>() {});
			table.entrySet().stream()
					.filter(e -> !Strings.isNullOrEmpty(e.getValue()))
					.forEach(e -> lines.add("- Preferred translation for \"%s\": %s.".formatted(e.getKey(), e.getValue())));
		} catch (Exception ignored) {
			// skip malformed table values
		}
	}

	private String formatFallback(Map<String, String> items) {
		List<String> lines = new ArrayList<>();
		items.entrySet().stream()
				.filter(e -> !Strings.isNullOrEmpty(e.getValue()))
				.forEach(e -> lines.add("- %s: %s".formatted(e.getKey(), e.getValue())));
		if (lines.isEmpty()) {
			return "";
		}
		return "Language policy:\n" + String.join("\n", lines);
	}
}
