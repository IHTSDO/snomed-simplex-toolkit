package org.snomed.simplex.service.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemValidationStatus;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateJobServiceTest {

	@Mock
	private SupportRegister supportRegister;

	@Mock
	private ActivityService activityService;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private ValidationServiceClient validationServiceClient;

	private ValidateJobService validateJobService;

	@BeforeEach
	void setUp() {
		validateJobService = spy(new ValidateJobService(
				supportRegister, activityService, snowstormClientFactory, validationServiceClient));
	}

	@Test
	void addValidationStatus_rvfUnreachable_isUnavailable() throws ServiceException {
		CodeSystem codeSystem = codeSystemWithReport("http://rvf.example/validation/123");
		when(validateJobService.getLatestJob("SNOMEDCT-TEST")).thenReturn(null);
		when(validationServiceClient.getValidation("http://rvf.example/validation/123"))
				.thenThrow(new ServiceException("Failed to fetch RVF validation report."));

		validateJobService.addValidationStatus(codeSystem);

		assertEquals(CodeSystemValidationStatus.UNAVAILABLE, codeSystem.getValidationStatus());
	}

	@Test
	void addValidationStatus_noJobAndNoReport_isTodo() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		when(validateJobService.getLatestJob("SNOMEDCT-TEST")).thenReturn(null);

		validateJobService.addValidationStatus(codeSystem);

		assertEquals(CodeSystemValidationStatus.TODO, codeSystem.getValidationStatus());
	}

	@Test
	void addValidationStatus_completeJobWithOlderHead_isStale() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setContentHeadTimestamp(200L);
		ExternalServiceJob job = new ExternalServiceJob(codeSystem, "Validation", "MAIN/SNOMEDCT-TEST", 100L);
		job.setStatus(JobStatus.COMPLETE);
		when(validateJobService.getLatestJob("SNOMEDCT-TEST")).thenReturn(job);

		validateJobService.addValidationStatus(codeSystem);

		assertEquals(CodeSystemValidationStatus.STALE, codeSystem.getValidationStatus());
	}

	private static CodeSystem codeSystemWithReport(String reportUrl) {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setLatestValidationReport(reportUrl);
		return codeSystem;
	}

}
