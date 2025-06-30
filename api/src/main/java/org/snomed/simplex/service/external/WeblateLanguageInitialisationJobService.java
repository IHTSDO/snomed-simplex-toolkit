package org.snomed.simplex.service.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateClientFactory;
import org.snomed.simplex.weblate.WeblateService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WeblateLanguageInitialisationJobService extends ExternalFunctionJobService<WeblateLanguageInitialisationRequest> {

	private final SnowstormClientFactory snowstormClientFactory;
	private final WeblateClientFactory weblateClientFactory;
	private final WeblateService weblateService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateLanguageInitialisationJobService(
			SupportRegister supportRegister,
			ActivityService activityService,
			SnowstormClientFactory snowstormClientFactory,
			WeblateClientFactory weblateClientFactory,
			WeblateService weblateService) {

		super(supportRegister, activityService);
		this.snowstormClientFactory = snowstormClientFactory;
		this.weblateClientFactory = weblateClientFactory;
		this.weblateService = weblateService;
	}

	@Override
	protected String getFunctionName() {
		return "Weblate language initialization";
	}

	@Override
	protected String doCallService(CodeSystem codeSystem, ExternalServiceJob asyncJob, WeblateLanguageInitialisationRequest request) throws ServiceException {
		SecurityContextHolder.setContext(asyncJob.getSecurityContext());

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		ConceptMini refset = snowstormClient.getRefsetOrThrow(request.refsetId(), codeSystem);

		String languageCode = codeSystem.getTranslationLanguages().get(request.refsetId());
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code is not set for refset.", HttpStatus.CONFLICT);
		}

		// Initialize the language and translation in Weblate
		String languageCodeWithRefset = "%s-%s".formatted(languageCode, request.refsetId());
		weblateService.initialiseLanguageAndTranslationAsync(refset, languageCodeWithRefset);

		// Return the language code with refset ID for monitoring
		logger.info("Started Weblate language initialization. Language:{}, refsetId:{}, jobId:{}",
				languageCodeWithRefset, request.refsetId(), asyncJob.getId());

		return languageCodeWithRefset;
	}

	@Override
	protected boolean doMonitorProgress(ExternalServiceJob job, String languageCodeWithRefset) {
		SecurityContextHolder.setContext(job.getSecurityContext());

		try {
			WeblateClient weblateClient = weblateClientFactory.getClient();
			Map<String, Object> statistics = weblateClient.getLanguageStatistics(languageCodeWithRefset);

			if (statistics != null) {
				Object totalObj = statistics.get("total");

				if (totalObj instanceof Integer total) {
					logger.debug("Language {} statistics - total: {}", languageCodeWithRefset, total);

					// Language has been processed when total is a positive number
					if (total > 0) {
						logger.info("Weblate language initialization completed. Language:{}, total:{}, jobId:{}",
								languageCodeWithRefset, total, job.getId());
						return true;
					}
				}
			}

			return false;
		} catch (Exception e) {
			logger.warn("Error monitoring Weblate language initialization for {}: {}", languageCodeWithRefset, e.getMessage());
			return false;
		}
	}
}
