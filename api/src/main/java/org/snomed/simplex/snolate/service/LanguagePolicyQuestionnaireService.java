package org.snomed.simplex.snolate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import jakarta.annotation.PostConstruct;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.LanguagePolicyOption;
import org.snomed.simplex.snolate.domain.LanguagePolicyQuestion;
import org.snomed.simplex.snolate.domain.LanguagePolicyQuestionnaire;
import org.snomed.simplex.snolate.domain.LanguagePolicySection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LanguagePolicyQuestionnaireService {

	private static final String QUESTIONNAIRE_RESOURCE = "/language-policy-questionnaire.json";

	private final ObjectMapper objectMapper;
	private LanguagePolicyQuestionnaire questionnaire;

	public LanguagePolicyQuestionnaireService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void init() throws IOException {
		try (InputStream inputStream = getClass().getResourceAsStream(QUESTIONNAIRE_RESOURCE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Missing questionnaire resource: " + QUESTIONNAIRE_RESOURCE);
			}
			questionnaire = objectMapper.readValue(inputStream, LanguagePolicyQuestionnaire.class);
		}
	}

	public LanguagePolicyQuestionnaire getQuestionnaire() {
		return questionnaire;
	}

	public String getCurrentVersion() {
		return questionnaire.version();
	}

	public void validatePolicyItems(String version, Map<String, String> items) throws ServiceExceptionWithStatusCode {
		String resolvedVersion = Strings.isNullOrEmpty(version) ? getCurrentVersion() : version;
		if (!getCurrentVersion().equals(resolvedVersion)) {
			throw new ServiceExceptionWithStatusCode("Unsupported questionnaire version: " + resolvedVersion, HttpStatus.BAD_REQUEST);
		}
		if (items == null || items.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Language policy items are required.", HttpStatus.BAD_REQUEST);
		}

		for (LanguagePolicySection section : questionnaire.sections()) {
			for (LanguagePolicyQuestion question : section.questions()) {
				validateQuestion(question, items);
			}
		}
	}

	private void validateQuestion(LanguagePolicyQuestion question, Map<String, String> items) throws ServiceExceptionWithStatusCode {
		String answer = items.get(question.id());
		if (question.required() && Strings.isNullOrEmpty(answer)) {
			throw new ServiceExceptionWithStatusCode("Required question not answered: " + question.title(), HttpStatus.BAD_REQUEST);
		}
		if (Strings.isNullOrEmpty(answer)) {
			return;
		}

		switch (question.type()) {
			case "choice", "boolean" -> validateChoice(question, answer, items);
			case "keyValueTable" -> validateKeyValueTable(question, answer);
			case "text" -> { /* any non-empty string is valid */ }
			default -> throw new ServiceExceptionWithStatusCode("Unknown question type: " + question.type(), HttpStatus.BAD_REQUEST);
		}
	}

	private void validateChoice(LanguagePolicyQuestion question, String answer, Map<String, String> items)
			throws ServiceExceptionWithStatusCode {
		List<LanguagePolicyOption> options = question.options();
		if (options == null || options.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Question has no options: " + question.id(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		Optional<LanguagePolicyOption> selected = options.stream().filter(o -> o.value().equals(answer)).findFirst();
		if (selected.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Invalid answer for question: " + question.title(), HttpStatus.BAD_REQUEST);
		}
		LanguagePolicyOption option = selected.get();
		if (Boolean.TRUE.equals(option.allowFreeText())) {
			String freeTextKey = option.freeTextKey();
			if (Strings.isNullOrEmpty(freeTextKey) || Strings.isNullOrEmpty(items.get(freeTextKey))) {
				throw new ServiceExceptionWithStatusCode("Free text required for question: " + question.title(), HttpStatus.BAD_REQUEST);
			}
		}
	}

	private void validateKeyValueTable(LanguagePolicyQuestion question, String answer) throws ServiceExceptionWithStatusCode {
		try {
			objectMapper.readValue(answer, LinkedHashMap.class);
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Invalid key-value table JSON for question: " + question.title(), HttpStatus.BAD_REQUEST);
		}
	}

	public List<LanguagePolicyQuestion> allQuestions() {
		List<LanguagePolicyQuestion> questions = new ArrayList<>();
		for (LanguagePolicySection section : questionnaire.sections()) {
			questions.addAll(section.questions());
		}
		return questions;
	}
}
