package org.snomed.simplex.snolate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;

import java.util.LinkedHashMap;
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
		service.validatePolicyItems("snomed-language-policy-v1", samplePolicyItems());
	}

	@Test
	void validatePolicyItems_rejectsMissingRequired() {
		Map<String, String> items = samplePolicyItems();
		items.remove("q1-compound-words");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items));
	}

	@Test
	void validatePolicyItems_rejectsInvalidChoice() {
		Map<String, String> items = samplePolicyItems();
		items.put("q3-articles", "INVALID");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items));
	}

	@Test
	void validatePolicyItems_rejectsOtherWithoutFreeText() {
		Map<String, String> items = samplePolicyItems();
		items.put("q1-compound-words", "OTHER");
		items.remove("q1-compound-words-other");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items));
	}

	@Test
	void validatePolicyItems_rejectsInvalidKeyValueTableJson() {
		Map<String, String> items = samplePolicyItems();
		items.put("q5-lexical-preferences", "not-json");
		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.validatePolicyItems("snomed-language-policy-v1", items));
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
}
