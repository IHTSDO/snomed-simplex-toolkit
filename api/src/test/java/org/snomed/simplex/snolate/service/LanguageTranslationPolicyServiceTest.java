package org.snomed.simplex.snolate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LanguageTranslationPolicyRequest;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.sets.LanguageTranslationPolicyRepository;
import org.snomed.simplex.translation.service.TranslationService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LanguageTranslationPolicyServiceTest {

	private static final String CODE_SYSTEM = "SNOMEDCT-ES";
	private static final String REFSET_ID = "450828004";

	@Mock
	private LanguageTranslationPolicyRepository policyRepository;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private TranslationService translationService;

	@Mock
	private SnowstormClient snowstormClient;

	private LanguageTranslationPolicyService service;

	@BeforeEach
	void setUp() throws Exception {
		LanguagePolicyQuestionnaireService questionnaireService = new LanguagePolicyQuestionnaireService(new ObjectMapper());
		questionnaireService.init();
		service = new LanguageTranslationPolicyService(
				policyRepository, snowstormClientFactory, translationService, questionnaireService);
	}

	@Test
	void upsert_rejectsBlankLanguageDialectName() {
		LanguageTranslationPolicyRequest request = sampleRequest("  ");

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.upsert(CODE_SYSTEM, REFSET_ID, request));

		assertEquals("Language/dialect name is required.", exception.getMessage());
		verify(policyRepository, never()).save(any());
	}

	@Test
	void upsert_rejectsNullLanguageDialectName() {
		LanguageTranslationPolicyRequest request = new LanguageTranslationPolicyRequest(
				"snomed-language-policy-v1", null, Map.of(), List.of());

		assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.upsert(CODE_SYSTEM, REFSET_ID, request));

		verify(policyRepository, never()).save(any());
	}

	@Test
	void upsert_persistsClientProvidedLanguageDialectName() throws Exception {
		stubRefsetMetadata("es");
		when(policyRepository.findById(LanguageTranslationPolicy.compositeId(CODE_SYSTEM, REFSET_ID)))
				.thenReturn(Optional.empty());
		when(policyRepository.save(any(LanguageTranslationPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LanguageTranslationPolicyRequest request = sampleRequest("Spanish (Latin America)");
		LanguageTranslationPolicy saved = service.upsert(CODE_SYSTEM, REFSET_ID, request);

		assertEquals("Spanish (Latin America)", saved.getLanguageDialectName());
		assertEquals("es", saved.getLanguageCode());

		ArgumentCaptor<LanguageTranslationPolicy> captor = ArgumentCaptor.forClass(LanguageTranslationPolicy.class);
		verify(policyRepository).save(captor.capture());
		assertEquals("Spanish (Latin America)", captor.getValue().getLanguageDialectName());
	}

	@Test
	void upsert_normalizesFullRefsetLanguageDialectName() throws Exception {
		stubRefsetMetadata("nl-be");
		when(policyRepository.findById(LanguageTranslationPolicy.compositeId(CODE_SYSTEM, REFSET_ID)))
				.thenReturn(Optional.empty());
		when(policyRepository.save(any(LanguageTranslationPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LanguageTranslationPolicy saved = service.upsert(CODE_SYSTEM, REFSET_ID,
				sampleRequest("Belgian Dutch language reference set"));

		assertEquals("Belgian Dutch", saved.getLanguageDialectName());
	}

	private LanguageTranslationPolicyRequest sampleRequest(String languageDialectName) {
		return new LanguageTranslationPolicyRequest(
				"snomed-language-policy-v1",
				languageDialectName,
				Map.of(),
				List.of());
	}

	private void stubRefsetMetadata(String languageCode) throws Exception {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setTranslationLanguages(Map.of(REFSET_ID, languageCode));
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM)).thenReturn(codeSystem);
		when(snowstormClient.getRefsetOrThrow(REFSET_ID, codeSystem)).thenReturn(mock(ConceptMini.class));
		when(translationService.listTranslations(codeSystem, snowstormClient)).thenReturn(List.of());
	}
}
