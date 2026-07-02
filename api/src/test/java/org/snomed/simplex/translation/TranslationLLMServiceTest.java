package org.snomed.simplex.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.ai.LLMService;
import org.snomed.simplex.ai.LlmCallContext;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.service.LanguagePolicyQuestionnaireServiceTest;
import org.snomed.simplex.snolate.service.LanguageTranslationPolicyService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationLLMServiceTest {

	@Mock
	private LLMService mockLLMService;

	@Mock
	private LanguageTranslationPolicyService mockPolicyService;

	@Mock
	private LanguagePolicyPromptFormatter mockPolicyFormatter;

	private TranslationLLMService translationLLMService;
	private SnolateTranslationSet mockTranslationSet;

	@BeforeEach
	void setUp() {
		translationLLMService = new TranslationLLMService(mockLLMService, mockPolicyService, mockPolicyFormatter);
		mockTranslationSet = mock(SnolateTranslationSet.class);
	}

	@Test
	void testSuggestTranslations_SingleSuggestion() {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-ES");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getLanguageCode()).thenReturn("es");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of("195967001|Asthma", "asma"));
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-ES", "450828004")).thenReturn(Optional.empty());
		when(mockPolicyFormatter.format(null)).thenReturn("");

		List<String> englishTerms = List.of("Heart attack", "Diabetes mellitus");
		when(mockLLMService.chat(anyString(), eq(false), any(LlmCallContext.class))).thenReturn("1|Ataque al corazón\n2|Diabetes mellitus");

		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		assertEquals(2, result.size());
		assertEquals(List.of("Ataque al corazón"), result.get("Heart attack"));
		verify(mockLLMService).chat(argThat(r -> r.contains("1|Asthma → asma")), eq(false), eq(new LlmCallContext("SNOMEDCT-ES", 2)));
	}

	@Test
	void testSuggestTranslations_WithLanguagePolicy() {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-IT");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getLanguageCode()).thenReturn("it");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of());

		LanguageTranslationPolicy policy = new LanguageTranslationPolicy();
		policy.setQuestionnaireVersion("snomed-language-policy-v1");
		policy.setPolicyItems(LanguagePolicyQuestionnaireServiceTest.samplePolicyItems());
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-IT", "450828004")).thenReturn(Optional.of(policy));
		when(mockPolicyFormatter.format(policy)).thenReturn("Language policy:\n- Use technical medical terminology.");

		when(mockLLMService.chat(anyString(), eq(false), any(LlmCallContext.class))).thenReturn("1|Polmonite");

		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, List.of("Pneumonia"), false, false);

		assertEquals(List.of("Polmonite"), result.get("Pneumonia"));
		verify(mockLLMService).chat(argThat(request ->
			request.contains("Language policy:") &&
			request.contains("Use technical medical terminology.")), eq(false), eq(new LlmCallContext("SNOMEDCT-IT", 1)));
	}

	@Test
	void testSuggestTranslations_FastMode() {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-DE");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getLanguageCode()).thenReturn("de");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of());
		when(mockPolicyService.findByCodeSystemAndRefset(anyString(), anyString())).thenReturn(Optional.empty());
		when(mockPolicyFormatter.format(null)).thenReturn("");

		when(mockLLMService.chat(anyString(), eq(true), any(LlmCallContext.class))).thenReturn("1|Fieber");

		translationLLMService.suggestTranslations(mockTranslationSet, List.of("Fever"), false, true);

		verify(mockLLMService).chat(anyString(), eq(true), eq(new LlmCallContext("SNOMEDCT-DE", 1)));
	}
}
