package org.snomed.simplex.service.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemClassificationStatus;
import org.snomed.simplex.client.domain.CodeSystemValidationStatus;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.snomed.simplex.domain.activity.ComponentType.CODE_SYSTEM;

@ExtendWith(MockitoExtension.class)
class ValidationWorkflowServiceTest {

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private SnowstormClient snowstormClient;

	@Mock
	private CodeSystemService codeSystemService;

	@Mock
	private ValidateJobService validateJobService;

	@Mock
	private ClassifyJobService classifyJobService;

	@Mock
	private ActivityService activityService;

	private ValidationWorkflowService validationWorkflowService;

	@BeforeEach
	void setUp() throws Exception {
		validationWorkflowService = new ValidationWorkflowService(
				snowstormClientFactory, codeSystemService, validateJobService, classifyJobService, activityService);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
	}

	@Test
	void startValidation_notClassified_startsValidationAndClassification() throws Exception {
		CodeSystem codeSystem = codeSystem(false);
		codeSystem.setValidationStatus(CodeSystemValidationStatus.TODO);
		codeSystem.setClassificationStatus(CodeSystemClassificationStatus.TODO);
		ExternalServiceJob validationJob = new ExternalServiceJob(codeSystem, "Validation");
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);
		when(activityService.startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM), eq(ActivityType.VALIDATE),
				eq(validateJobService), isNull())).thenReturn(validationJob);

		ExternalServiceJob result = validationWorkflowService.startValidation("SNOMEDCT-TEST");

		assertEquals(validationJob, result);
		verify(snowstormClient).invalidateCodeSystemCache("SNOMEDCT-TEST");
		verify(activityService).startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM), eq(ActivityType.CLASSIFY),
				eq(classifyJobService), isNull());
	}

	@Test
	void startValidation_classified_startsValidationOnly() throws Exception {
		CodeSystem codeSystem = codeSystem(true);
		codeSystem.setValidationStatus(CodeSystemValidationStatus.STALE);
		codeSystem.setClassificationStatus(CodeSystemClassificationStatus.COMPLETE);
		ExternalServiceJob validationJob = new ExternalServiceJob(codeSystem, "Validation");
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);
		when(activityService.startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM), eq(ActivityType.VALIDATE),
				eq(validateJobService), isNull())).thenReturn(validationJob);

		validationWorkflowService.startValidation("SNOMEDCT-TEST");

		verify(activityService, never()).startExternalServiceActivity(eq(codeSystem), eq(CODE_SYSTEM),
				eq(ActivityType.CLASSIFY), eq(classifyJobService), isNull());
	}

	@Test
	void startValidation_validationInProgress_throwsConflict() throws Exception {
		CodeSystem codeSystem = codeSystem(true);
		codeSystem.setValidationStatus(CodeSystemValidationStatus.IN_PROGRESS);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> validationWorkflowService.startValidation("SNOMEDCT-TEST"));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
		verify(activityService, never()).startExternalServiceActivity(any(), any(), any(), any(), any());
	}

	@Test
	void startValidation_classificationInProgressWhenNotClassified_throwsConflict() throws Exception {
		CodeSystem codeSystem = codeSystem(false);
		codeSystem.setClassificationStatus(CodeSystemClassificationStatus.IN_PROGRESS);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> validationWorkflowService.startValidation("SNOMEDCT-TEST"));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
	}

	private static CodeSystem codeSystem(boolean classified) {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setClassified(classified);
		return codeSystem;
	}

}
