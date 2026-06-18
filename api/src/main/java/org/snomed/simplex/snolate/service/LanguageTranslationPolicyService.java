package org.snomed.simplex.snolate.service;

import com.google.common.base.Strings;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LanguageTranslationPolicyRequest;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.sets.LanguageTranslationPolicyRepository;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class LanguageTranslationPolicyService {

	private final LanguageTranslationPolicyRepository policyRepository;
	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final LanguagePolicyQuestionnaireService questionnaireService;

	public LanguageTranslationPolicyService(LanguageTranslationPolicyRepository policyRepository,
			SnowstormClientFactory snowstormClientFactory, TranslationService translationService,
			LanguagePolicyQuestionnaireService questionnaireService) {
		this.policyRepository = policyRepository;
		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.questionnaireService = questionnaireService;
	}

	public List<LanguageTranslationPolicy> findByCodeSystem(String codeSystem) {
		return policyRepository.findByCodesystemOrderByDisplayName(codeSystem);
	}

	public Optional<LanguageTranslationPolicy> findByCodeSystemAndRefset(String codeSystem, String refsetId) {
		return policyRepository.findByCodesystemAndRefset(codeSystem, refsetId);
	}

	public LanguageTranslationPolicy getOrThrow(String codeSystem, String refsetId) throws ServiceExceptionWithStatusCode {
		return findByCodeSystemAndRefset(codeSystem, refsetId)
				.orElseThrow(() -> new ServiceExceptionWithStatusCode("Language translation policy not found.", HttpStatus.NOT_FOUND));
	}

	public LanguageTranslationPolicy upsert(String codeSystem, String refsetId, LanguageTranslationPolicyRequest request)
			throws ServiceException {

		if (request.policyItems() == null) {
			throw new ServiceExceptionWithStatusCode("Language policy items are required.", HttpStatus.BAD_REQUEST);
		}
		String version = Strings.isNullOrEmpty(request.questionnaireVersion())
				? questionnaireService.getCurrentVersion()
				: request.questionnaireVersion();
		questionnaireService.validatePolicyItems(version, request.policyItems());

		RefsetMetadata metadata = resolveRefsetMetadata(codeSystem, refsetId);

		String id = LanguageTranslationPolicy.compositeId(codeSystem, refsetId);
		LanguageTranslationPolicy policy = policyRepository.findById(id).orElseGet(LanguageTranslationPolicy::new);
		Date now = new Date();
		if (policy.getCreated() == null) {
			policy.setCreated(now);
		}
		policy.setId(id);
		policy.setCodesystem(codeSystem);
		policy.setRefset(refsetId);
		policy.setLanguageCode(metadata.languageCode());
		policy.setDisplayName(metadata.displayName());
		policy.setQuestionnaireVersion(version);
		policy.setPolicyItems(request.policyItems());
		policy.setLastModified(now);
		return policyRepository.save(policy);
	}

	private RefsetMetadata resolveRefsetMetadata(String codeSystem, String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);

		String languageCode = theCodeSystem.getTranslationLanguages().get(refsetId);
		String displayName = refsetId;

		List<ConceptMini> translations = translationService.listTranslations(theCodeSystem, snowstormClient);
		for (ConceptMini translation : translations) {
			if (refsetId.equals(translation.getConceptId())) {
				displayName = translation.getPtOrFsnOrConceptId();
				if (languageCode == null) {
					Object lang = translation.getExtraFields().get("lang");
					if (lang instanceof String langStr) {
						languageCode = langStr;
					}
				}
				break;
			}
		}

		if (Strings.isNullOrEmpty(languageCode)) {
			languageCode = "en";
		}
		return new RefsetMetadata(languageCode, displayName);
	}

	private record RefsetMetadata(String languageCode, String displayName) {
	}
}
