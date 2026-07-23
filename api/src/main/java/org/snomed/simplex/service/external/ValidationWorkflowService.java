package org.snomed.simplex.service.external;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemClassificationStatus;
import org.snomed.simplex.client.domain.CodeSystemValidationStatus;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@Service
public class ValidationWorkflowService {

	private final SnowstormClientFactory snowstormClientFactory;
	private final CodeSystemService codeSystemService;
	private final ValidateJobService validateJobService;
	private final ClassifyJobService classifyJobService;
	private final ActivityService activityService;

	public ValidationWorkflowService(
			SnowstormClientFactory snowstormClientFactory,
			CodeSystemService codeSystemService,
			ValidateJobService validateJobService,
			ClassifyJobService classifyJobService,
			ActivityService activityService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.codeSystemService = codeSystemService;
		this.validateJobService = validateJobService;
		this.classifyJobService = classifyJobService;
		this.activityService = activityService;
	}

	public ExternalServiceJob startValidation(String codeSystemShortName) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.invalidateCodeSystemCache(codeSystemShortName);
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(codeSystemShortName);

		codeSystemService.addClassificationStatus(codeSystem);
		validateJobService.addValidationStatus(codeSystem);

		if (codeSystem.getValidationStatus() == CodeSystemValidationStatus.IN_PROGRESS) {
			throw new ServiceExceptionWithStatusCode("Validation is already in progress.", HttpStatus.CONFLICT);
		}
		if (!codeSystem.isClassified()
				&& codeSystem.getClassificationStatus() == CodeSystemClassificationStatus.IN_PROGRESS) {
			throw new ServiceExceptionWithStatusCode("Classification is already in progress.", HttpStatus.CONFLICT);
		}

		ExternalServiceJob validationJob = activityService.startExternalServiceActivity(
				codeSystem, CODE_SYSTEM, ActivityType.VALIDATE, validateJobService, null);

		if (!codeSystem.isClassified()) {
			activityService.startExternalServiceActivity(
					codeSystem, CODE_SYSTEM, ActivityType.CLASSIFY, classifyJobService, null);
		}

		return validationJob;
	}

}
