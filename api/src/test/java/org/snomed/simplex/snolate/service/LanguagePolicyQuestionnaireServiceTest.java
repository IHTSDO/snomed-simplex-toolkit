package org.snomed.simplex.snolate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LanguagePolicyQuestionnaireServiceTest {

	private LanguagePolicyQuestionnaireService service;

	@BeforeEach
	void setUp() throws Exception {
		service = new LanguagePolicyQuestionnaireService(new ObjectMapper());
		service.init();
	}

	@Test
	void getQuestionnaire_loadsFromResource() {
		assertEquals("snomed-language-policy-v1", service.getCurrentVersion());
		assertFalse(service.getQuestionnaire().sections().isEmpty());
	}

	@Test
	void validatePolicyItems_acceptsCompletePolicy() throws ServiceExceptionWithStatusCode {
		service.validatePolicyItems("snomed-language-policy-v1", samplePolicyItems(), sampleSelectedRules());
	}

	@Test
	void validatePolicyItems_acceptsPartialSelection() throws ServiceExceptionWithStatusCode {
		Map<String, String> items = new LinkedHashMap<>();
		items.put("q1-compound-words", "COMPOUND");
		items.put("q6-clinical-register", "TECHNICAL");
		service.validatePolicyItems("snomed-language-policy-v1", items, List.of("q1-compound-words", "q6-clinical-register"));
	}

	@Test
	void validatePolicyItems_acceptsEmptySelection() throws ServiceExceptionWithStatusCode {
		service.validatePolicyItems("snomed-language-policy-v1", Map.of(), List.of());
	}

	@Test
	void validatePolicyItems_rejectsUnknownSelectedRule() {
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", samplePolicyItems(), List.of("unknown-rule")));
	}

	@Test
	void validatePolicyItems_rejectsMissingSelectedAnswer() {
		Map<String, String> items = samplePolicyItems();
		items.remove("q1-compound-words");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items, sampleSelectedRules()));
	}

	@Test
	void validatePolicyItems_ignoresUnselectedQuestions() throws ServiceExceptionWithStatusCode {
		Map<String, String> items = new LinkedHashMap<>();
		items.put("q1-compound-words", "COMPOUND");
		service.validatePolicyItems("snomed-language-policy-v1", items, List.of("q1-compound-words"));
	}

	@Test
	void validatePolicyItems_rejectsInvalidChoice() {
		Map<String, String> items = samplePolicyItems();
		items.put("q3-articles", "INVALID");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items, sampleSelectedRules()));
	}

	@Test
	void validatePolicyItems_rejectsOtherWithoutFreeText() {
		Map<String, String> items = samplePolicyItems();
		items.put("q1-compound-words", "OTHER");
		items.remove("q1-compound-words-other");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items, sampleSelectedRules()));
	}

	@Test
	void validatePolicyItems_rejectsInvalidKeyValueTableJson() {
		Map<String, String> items = samplePolicyItems();
		items.put("q5-lexical-preferences", "not-json");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items, sampleSelectedRules()));
	}

	public static Map<String, String> samplePolicyItems() {
		Map<String, String> items = new LinkedHashMap<>();
		items.put("q1-compound-words", "COMPOUND");
		items.put("q2-adjectival-forms", "ADJECTIVAL");
		items.put("q3-articles", "USE_ARTICLES");
		items.put("q4-possessive", "POSSESSIVE");
		items.put("q5-lexical-normalization", "ONE_APPROVED");
		items.put("q5-lexical-preferences", "{\"Disorder\":\"Trastorno\"}");
		items.put("q6-clinical-register", "TECHNICAL");
		items.put("q7-eponyms", "PREFER_EPONYMS");
		items.put("q8-acronyms", "NEVER_PREFERRED");
		items.put("q9-word-order", "ADJECTIVE_BASED");
		items.put("q10-capitalization", "NATIONAL_STANDARDS");
		items.put("q12-consistency", "ALWAYS_SAME");
		items.put("q13-reuse", "REUSE_SIBLINGS");
		items.put("q14-validation", "true");
		return items;
	}

	public static List<String> sampleSelectedRules() {
		return List.of(
				"q1-compound-words",
				"q2-adjectival-forms",
				"q3-articles",
				"q4-possessive",
				"q5-lexical-normalization",
				"q5-lexical-preferences",
				"q6-clinical-register",
				"q7-eponyms",
				"q8-acronyms",
				"q9-word-order",
				"q10-capitalization",
				"q12-consistency",
				"q13-reuse",
				"q14-validation"
		);
	}
}
