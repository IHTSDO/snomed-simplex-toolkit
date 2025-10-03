package org.snomed.simplex.weblate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.ai.LLMService;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationLLMServiceTest {

	@Mock
	private LLMService mockLLMService;

	private TranslationLLMService translationLLMService;
	private WeblateTranslationSet mockTranslationSet;

	@BeforeEach
	void setUp() {
		translationLLMService = new TranslationLLMService(mockLLMService);
		mockTranslationSet = mock(WeblateTranslationSet.class);
	}

	@Test
	void testSuggestTranslations_SingleSuggestion() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("es");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Heart attack", "Diabetes mellitus");
		String mockResponse = "1|Ataque al corazón\n2|Diabetes mellitus";
		
		ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
		when(mockLLMService.chat(requestCaptor.capture(), eq(false))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertTrue(result.containsKey("Heart attack"));
		assertTrue(result.containsKey("Diabetes mellitus"));
		assertEquals(List.of("Ataque al corazón"), result.get("Heart attack"));
		assertEquals(List.of("Diabetes mellitus"), result.get("Diabetes mellitus"));

		// Verify LLM service was called with correct parameters and assert the actual request string
		verify(mockLLMService).chat(requestCaptor.capture(), eq(false));
		String actualRequest = requestCaptor.getValue();
		
		String expectedRequest = """
			Translate the following clinical terminology terms from English to es.
			For each term provided, return the term number and the es translation.
			Use the exact formatting below:
			<term number>|<translation>
			Guidelines:
			- Provide one translation after each line number. If a translation cannot be found output the line number and pipe but leave the translation blank.
			- Preserve the original order of the terms; do not reorder, group, or summarize them.
			- Preserve all modifiers, qualifiers, any body location descriptors.
			- Set reasoning_effort = minimal; outputs should be terse, limited to the requested direct translations in plain text.


			English terms:
			1|Heart attack
			2|Diabetes mellitus
			""";
		
		assertEquals(expectedRequest.trim(), actualRequest.trim());
	}

	@Test
	void testSuggestTranslations_MultipleSuggestions() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("fr");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Chest pain", "High blood pressure");
		String mockResponse = "1|Douleur thoracique|Douleur de poitrine\n2|Hypertension artérielle|Pression artérielle élevée";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, true, false);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertTrue(result.containsKey("Chest pain"));
		assertTrue(result.containsKey("High blood pressure"));
		assertEquals(List.of("Douleur thoracique", "Douleur de poitrine"), result.get("Chest pain"));
		assertEquals(List.of("Hypertension artérielle", "Pression artérielle élevée"), result.get("High blood pressure"));

		// Verify LLM service was called with correct parameters
		verify(mockLLMService).chat(anyString(), eq(false));
	}

	@Test
	void testSuggestTranslations_FastMode() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("de");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Fever");
		String mockResponse = "1|Fieber";
		
		when(mockLLMService.chat(anyString(), eq(true))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, true);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue(result.containsKey("Fever"));
		assertEquals(List.of("Fieber"), result.get("Fever"));

		// Verify LLM service was called with fast mode enabled
		verify(mockLLMService).chat(anyString(), eq(true));
	}

	@Test
	void testSuggestTranslations_WithLanguageAdvice() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("it");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn("Use medical terminology appropriate for Italian healthcare professionals.");
		
		List<String> englishTerms = List.of("Pneumonia");
		String mockResponse = "1|Polmonite";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue(result.containsKey("Pneumonia"));
		assertEquals(List.of("Polmonite"), result.get("Pneumonia"));

		// Verify LLM service was called and the request contains language advice
		verify(mockLLMService).chat(argThat(request -> 
			request.contains("Use medical terminology appropriate for Italian healthcare professionals.")), eq(false));
	}

	@Test
	void testSuggestTranslations_EmptyResponse() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("pt");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Headache");
		String mockResponse = "";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testSuggestTranslations_InvalidResponseFormat() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("nl");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Back pain", "Headache");
		String mockResponse = "1|Rugpijn\ninvalid_line_without_pipe\n2|Hoofdpijn";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertTrue(result.containsKey("Back pain"));
		assertTrue(result.containsKey("Headache"));
		assertEquals(List.of("Rugpijn"), result.get("Back pain"));
		assertEquals(List.of("Hoofdpijn"), result.get("Headache"));
	}

	@Test
	void testSuggestTranslations_InvalidTermNumber() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("sv");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Cough", "Sneeze");
		String mockResponse = "1|Host\nabc|Invalid term number\n2|Nys";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		Map<String, List<String>> result = translationLLMService.suggestTranslations(
			mockTranslationSet, englishTerms, false, false);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertTrue(result.containsKey("Cough"));
		assertTrue(result.containsKey("Sneeze"));
		assertEquals(List.of("Host"), result.get("Cough"));
		assertEquals(List.of("Nys"), result.get("Sneeze"));
		// The invalid line should be ignored
	}

	@Test
	void testSuggestTranslations_RequestFormat() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("ja");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn("Use hiragana and katakana appropriately.");
		
		List<String> englishTerms = List.of("Stomach ache", "Nausea");
		String mockResponse = "1|胃痛\n2|吐き気";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		translationLLMService.suggestTranslations(mockTranslationSet, englishTerms, false, false);

		// Assert - Verify the request format contains expected elements
		verify(mockLLMService).chat(argThat(request -> 
			request.contains("Translate the following clinical terminology terms from English to ja.") &&
			request.contains("For each term provided, return the term number and the ja translation.") &&
			request.contains("Use the exact formatting below:") &&
			request.contains("<term number>|<translation>") &&
			request.contains("Use hiragana and katakana appropriately.") &&
			request.contains("English terms:") &&
			request.contains("1|Stomach ache") &&
			request.contains("2|Nausea") &&
			request.contains("Guidelines:") &&
			request.contains("one translation")
		), eq(false));
	}

	@Test
	void testSuggestTranslations_MultipleSuggestionsRequestFormat() {
		// Arrange
		when(mockTranslationSet.getLanguageCode()).thenReturn("ko");
		when(mockTranslationSet.getAiLanguageAdvice()).thenReturn(null);
		
		List<String> englishTerms = List.of("Dizziness");
		String mockResponse = "1|현기증|어지러움";
		
		when(mockLLMService.chat(anyString(), eq(false))).thenReturn(mockResponse);

		// Act
		translationLLMService.suggestTranslations(mockTranslationSet, englishTerms, true, false);

		// Assert - Verify the request format for multiple suggestions
		verify(mockLLMService).chat(argThat(request -> 
			request.contains("For each term provided, return the term number and the top two ko translations.") &&
			request.contains("<term number>|<translation1>|<translation2>") &&
			request.contains("two translations")
		), eq(false));
	}
}
