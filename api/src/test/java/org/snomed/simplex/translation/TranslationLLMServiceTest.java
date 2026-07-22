package org.snomed.simplex.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.ai.LLMService;
import org.snomed.simplex.ai.LlmCallContext;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
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
	void testSuggestTranslations_SingleSuggestion() throws Exception {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-ES");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of("195967001|Asthma", "asma"));

		LanguageTranslationPolicy policy = policyWithLanguageDialectName("Spanish");
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-ES", "450828004")).thenReturn(Optional.of(policy));
		when(mockPolicyFormatter.format(policy)).thenReturn("");

		List<String> englishTerms = List.of("Heart attack", "Diabetes mellitus");
		when(mockLLMService.chat(anyString(), eq(false), any(LlmCallContext.class))).thenReturn("1|Ataque al corazón\n2|Diabetes mellitus");

		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		assertEquals(2, result.size());
		assertEquals(List.of("Ataque al corazón"), result.get("Heart attack"));
		verify(mockLLMService).chat(argThat(r ->
				r.contains("Translate the following clinical terminology terms from English to Spanish.") &&
				r.contains("the Spanish translation") &&
				!r.contains(" to es.") &&
				r.contains("1|Asthma → asma")), eq(false), eq(new LlmCallContext("SNOMEDCT-ES", 2)));
	}

	@Test
	void testSuggestTranslations_WithLanguagePolicy() throws Exception {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-IT");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of());

		LanguageTranslationPolicy policy = policyWithLanguageDialectName("Italian");
		policy.setQuestionnaireVersion("snomed-language-policy-v1");
		policy.setPolicyItems(LanguagePolicyQuestionnaireServiceTest.samplePolicyItems());
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-IT", "450828004")).thenReturn(Optional.of(policy));
		when(mockPolicyFormatter.format(policy)).thenReturn("Language policy:\n- Use technical medical terminology.");

		when(mockLLMService.chat(anyString(), eq(false), any(LlmCallContext.class))).thenReturn("1|Polmonite");

		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, List.of("Pneumonia"), false, false);

		assertEquals(List.of("Polmonite"), result.get("Pneumonia"));
		verify(mockLLMService).chat(argThat(request ->
			request.contains("Translate the following clinical terminology terms from English to Italian.") &&
			request.contains("Language policy:") &&
			request.contains("Use technical medical terminology.")), eq(false), eq(new LlmCallContext("SNOMEDCT-IT", 1)));
	}

	@Test
	void testSuggestTranslations_FastMode() throws Exception {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-DE");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of());

		LanguageTranslationPolicy policy = policyWithLanguageDialectName("German");
		when(mockPolicyService.findByCodeSystemAndRefset(anyString(), anyString())).thenReturn(Optional.of(policy));
		when(mockPolicyFormatter.format(policy)).thenReturn("");

		when(mockLLMService.chat(anyString(), eq(true), any(LlmCallContext.class))).thenReturn("1|Fieber");

		translationLLMService.suggestTranslations(mockTranslationSet, List.of("Fever"), false, true);

		verify(mockLLMService).chat(argThat(r -> r.contains("English to German.")), eq(true), eq(new LlmCallContext("SNOMEDCT-DE", 1)));
	}

	@Test
	void testSuggestTranslations_rejectsMissingPolicy() {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-ES");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-ES", "450828004")).thenReturn(Optional.empty());

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> translationLLMService.suggestTranslations(mockTranslationSet, List.of("Fever"), false, false));

		assertEquals("Language translation policy with dialect name is required before running AI translation.",
				exception.getMessage());
		verifyNoInteractions(mockLLMService);
	}

	@Test
	void testSuggestTranslations_normalizesFullRefsetLanguageDialectName() throws Exception {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-BE");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of());

		LanguageTranslationPolicy policy = policyWithLanguageDialectName("Belgian Dutch language reference set");
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-BE", "450828004")).thenReturn(Optional.of(policy));
		when(mockPolicyFormatter.format(policy)).thenReturn("");

		when(mockLLMService.chat(anyString(), eq(false), any(LlmCallContext.class))).thenReturn("1|Koorts");

		translationLLMService.suggestTranslations(mockTranslationSet, List.of("Fever"), false, false);

		verify(mockLLMService).chat(argThat(r ->
				r.contains("Translate the following clinical terminology terms from English to Belgian Dutch.") &&
				r.contains("the Belgian Dutch translation") &&
				!r.contains("language reference set")), eq(false), any(LlmCallContext.class));
	}

	@Test
	void testSuggestTranslations_rejectsBlankLanguageDialectName() {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-ES");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");

		LanguageTranslationPolicy policy = policyWithLanguageDialectName("  ");
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-ES", "450828004")).thenReturn(Optional.of(policy));

		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> translationLLMService.suggestTranslations(mockTranslationSet, List.of("Fever"), false, false));

		verifyNoInteractions(mockLLMService);
	}

	@Test
	void testSuggestBatchTranslations_mixedPromptAndSparseLineNumbers() throws Exception {
		when(mockTranslationSet.getCodesystem()).thenReturn("SNOMEDCT-ES");
		when(mockTranslationSet.getRefset()).thenReturn("450828004");
		when(mockTranslationSet.getAiGoldenSet()).thenReturn(Map.of());

		LanguageTranslationPolicy policy = policyWithLanguageDialectName("Spanish");
		when(mockPolicyService.findByCodeSystemAndRefset("SNOMEDCT-ES", "450828004")).thenReturn(Optional.of(policy));
		when(mockPolicyFormatter.format(policy)).thenReturn("");

		BatchTranslationPrompt prompt = BatchTranslationPrompt.builder()
				.addContextLine("Asthma", "Asma")
				.addContextLine("Diabetes mellitus", "Diabetes")
				.addTranslateLine("Heart failure")
				.addTranslateLine("Pneumonia")
				.build();

		when(mockLLMService.chat(anyString(), eq(false), any(LlmCallContext.class)))
				.thenReturn("3|Insuficiencia cardíaca\n4|Neumonía");

		Map<String, List<String>> result = translationLLMService.suggestBatchTranslations(mockTranslationSet, prompt);

		assertEquals(List.of("Insuficiencia cardíaca"), result.get("Heart failure"));
		assertEquals(List.of("Neumonía"), result.get("Pneumonia"));
		verify(mockLLMService).chat(argThat(request ->
				request.contains("English to Spanish.") &&
				request.contains("the Spanish translation") &&
				request.contains("1|Asthma → Asma") &&
				request.contains("2|Diabetes mellitus → Diabetes") &&
				request.contains("3|Heart failure") &&
				request.contains("4|Pneumonia") &&
				request.contains("do not return translations for those lines")), eq(false), eq(new LlmCallContext("SNOMEDCT-ES", 2)));
	}

	private static LanguageTranslationPolicy policyWithLanguageDialectName(String languageDialectName) {
		LanguageTranslationPolicy policy = new LanguageTranslationPolicy();
		policy.setLanguageDialectName(languageDialectName);
		return policy;
	}
}
