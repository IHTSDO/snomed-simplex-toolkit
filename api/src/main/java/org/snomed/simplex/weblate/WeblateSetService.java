package org.snomed.simplex.weblate;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.snomed.simplex.weblate.domain.WeblateLabel;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

@Service
public class WeblateSetService {

	private static final String JOB_MESSAGE_USERNAME = "username";
	private static final String JOB_MESSAGE_ID = "id";

	private final WeblateSetRepository weblateSetRepository;
	private final WeblateClientFactory weblateClientFactory;
	private final SnowstormClientFactory snowstormClientFactory;

	private final JmsTemplate jmsTemplate;
	private final String jmsQueuePrefix;

	private final Map<String, SecurityContext> translationSetUserIdToUserContextMap;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateSetService(WeblateSetRepository weblateSetRepository, WeblateClientFactory weblateClientFactory, SnowstormClientFactory snowstormClientFactory,
			JmsTemplate jmsTemplate, @Value("${jms.queue.prefix}") String jmsQueuePrefix) {

		this.weblateSetRepository = weblateSetRepository;
		this.weblateClientFactory = weblateClientFactory;
		this.snowstormClientFactory = snowstormClientFactory;
		this.jmsTemplate = jmsTemplate;
		this.jmsQueuePrefix = jmsQueuePrefix;
		translationSetUserIdToUserContextMap = new HashMap<>();
	}


	public List<WeblateTranslationSet> findByCodeSystemAndRefset(String codeSystem, String refsetId) {
		return weblateSetRepository.findByCodesystemAndRefset(codeSystem, refsetId);
	}

	public WeblateTranslationSet createSet(WeblateTranslationSet translationSet) throws ServiceExceptionWithStatusCode {
		String codesystemShortName = translationSet.getCodesystem();
		ServiceHelper.requiredParameter("codesystem", codesystemShortName);
		ServiceHelper.requiredParameter("name", translationSet.getName());
		String refsetId = translationSet.getRefset();
		ServiceHelper.requiredParameter("refset", refsetId);
		ServiceHelper.requiredParameter("label", translationSet.getLabel());
		ServiceHelper.requiredParameter("ecl", translationSet.getEcl());
		ServiceHelper.requiredParameter("branchPath", translationSet.getBranchPath());

		Optional<WeblateTranslationSet> optional = weblateSetRepository.findByCodesystemAndLabelAndRefset(codesystemShortName, translationSet.getLabel(), refsetId);
		if (optional.isPresent()) {
			throw new ServiceExceptionWithStatusCode("A translation set with this label already exists.", HttpStatus.CONFLICT);
		}

		CodeSystem codeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(codesystemShortName);
		String language = codeSystem.getTranslationLanguages().get(refsetId);
		if (language == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset: " + refsetId, HttpStatus.NOT_FOUND);
		}
		String languageCodeWithRefsetId = "%s-%s".formatted(language, refsetId);

		WeblateClient weblateClient = weblateClientFactory.getClient();
		if (!weblateClient.isTranslationExistsSearchByLanguageRefset(languageCodeWithRefsetId)) {
			throw new ServiceExceptionWithStatusCode("Translation does not exist in Weblate, " +
					"please start language initialisation job or wait for it to finish.", HttpStatus.CONFLICT);
		}

		translationSet.setStatus(TranslationSetStatus.INITIALISING);

		logger.info("Creating Weblate Translation Set {}/{}/{}", codesystemShortName, refsetId, translationSet.getLabel());
		weblateSetRepository.save(translationSet);
		String username = SecurityUtil.getUsername();
		translationSetUserIdToUserContextMap.put(username, SecurityContextHolder.getContext());

		jmsTemplate.convertAndSend(jmsQueuePrefix + ".translation-set.processing", Map.of(JOB_MESSAGE_USERNAME, username, JOB_MESSAGE_ID, translationSet.getId()));
		return translationSet;
	}

	@JmsListener(destination = "${jms.queue.prefix}.translation-set.processing", concurrency = "1")
	public void processTranslationSet(Map<String, String> jobMessage) {
		String username = jobMessage.get(JOB_MESSAGE_USERNAME);
		String translationSetId = jobMessage.get(JOB_MESSAGE_ID);
		Optional<WeblateTranslationSet> optional = weblateSetRepository.findById(translationSetId);
		if (optional.isEmpty()) {
			logger.info("Weblate set was deleted before being processed {}", translationSetId);
			return;
		}

		WeblateTranslationSet translationSet = optional.get();
		logger.info("Processing translation set: {}/{}/{}", translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

		try {
			SecurityContextHolder.setContext(translationSetUserIdToUserContextMap.get(username));
			// Update status to processing
			translationSet.setStatus(TranslationSetStatus.PROCESSING);
			weblateSetRepository.save(translationSet);

			// Get the Weblate client
			WeblateClient weblateClient = weblateClientFactory.getClient();

			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			Supplier<String> conceptIdStream = snowstormClient.getConceptIdStream(translationSet.getBranchPath(), translationSet.getEcl());

			String code;
			String label = "%s_%s_%s".formatted(translationSet.getCodesystem().replace("SNOMEDCT-", ""),
				translationSet.getRefset(), translationSet.getLabel());

			WeblateLabel weblateLabel = weblateClient.getCreateLabel(WeblateClient.COMMON_PROJECT, label);

			while ((code = conceptIdStream.get()) != null) {
				WeblateUnit unit = weblateClient.getUnitForConceptId(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT, code);
				List<WeblateLabel> labels = new ArrayList<>(unit.getLabels());
				labels.add(weblateLabel);
				logger.info("Adding label:{} to unit id:{} concept:{}", label, unit.getId(), unit.getContext());
				weblateClient.patchUnitLabels(unit.getId(), labels);
			}

			translationSet.setStatus(TranslationSetStatus.COMPLETED);
			weblateSetRepository.save(translationSet);

			logger.info("Successfully processed translation set: {}/{}/{}",
					translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());

		} catch (Exception e) {
			logger.error("Error processing translation set: {}/{}/{}",
					translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel(), e);

			// Update status to failed
			translationSet.setStatus(TranslationSetStatus.FAILED);
			weblateSetRepository.save(translationSet);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
