package org.snomed.simplex.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.service.LanguagePolicyQuestionnaireService;
import org.snomed.simplex.snolate.service.LanguagePolicyQuestionnaireServiceTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguagePolicyPromptFormatterTest {

	private LanguagePolicyPromptFormatter formatter;

	@BeforeEach
	void setUp() throws Exception {
		LanguagePolicyQuestionnaireService questionnaireService = new LanguagePolicyQuestionnaireService(new ObjectMapper());
		questionnaireService.init();
		formatter = new LanguagePolicyPromptFormatter(questionnaireService, new ObjectMapper());
	}

	@Test
	void formatReturnsEmptyForNullPolicy() {
		assertEquals("", formatter.format(null));
	}

	@Test
	void formatIncludesPolicyDirectivesFromQuestionnaire() {
		LanguageTranslationPolicy policy = new LanguageTranslationPolicy();
		policy.setQuestionnaireVersion("snomed-language-policy-v1");
		policy.setPolicyItems(LanguagePolicyQuestionnaireServiceTest.samplePolicyItems());

		String formatted = formatter.format(policy);

		assertTrue(formatted.startsWith("Language policy:"));
		assertTrue(formatted.contains("Prefer compound words"));
		assertTrue(formatted.contains("Use technical medical terminology"));
		assertTrue(formatted.contains("Preferred translation for \"Disorder\": Trastorno"));
		assertTrue(formatted.contains("Flag translations that violate"));
	}

	@Test
	void formatIncludesOnlySelectedRules() {
		LanguageTranslationPolicy policy = new LanguageTranslationPolicy();
		policy.setQuestionnaireVersion("snomed-language-policy-v1");
		policy.setPolicyItems(LanguagePolicyQuestionnaireServiceTest.samplePolicyItems());
		policy.setSelectedRules(List.of("q1-compound-words", "q6-clinical-register"));

		String formatted = formatter.format(policy);

		assertTrue(formatted.contains("Prefer compound words"));
		assertTrue(formatted.contains("Use technical medical terminology"));
		assertFalse(formatted.contains("Prefer adjectival forms"));
		assertFalse(formatted.contains("Flag translations that violate"));
	}

	@Test
	void formatOtherOptionIncludesFreeText() {
		LanguageTranslationPolicy policy = new LanguageTranslationPolicy();
		policy.setQuestionnaireVersion("snomed-language-policy-v1");
		var items = LanguagePolicyQuestionnaireServiceTest.samplePolicyItems();
		items.put("q1-compound-words", "OTHER");
		items.put("q1-compound-words-other", "Use compounds for anatomy only");
		policy.setPolicyItems(items);

		String formatted = formatter.format(policy);
		assertTrue(formatted.contains("Use compounds for anatomy only"));
	}
}
