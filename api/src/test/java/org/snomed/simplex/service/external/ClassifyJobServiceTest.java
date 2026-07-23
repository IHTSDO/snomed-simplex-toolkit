package org.snomed.simplex.service.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemClassificationStatus;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifyJobServiceTest {

	@Mock
	private SupportRegister supportRegister;

	@Mock
	private ActivityService activityService;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	private ClassifyJobService classifyJobService;

	@BeforeEach
	void setUp() {
		classifyJobService = spy(new ClassifyJobService(supportRegister, activityService, snowstormClientFactory));
	}

	@Test
	void addClassificationStatus_classifiedTrue_isComplete() {
		CodeSystem codeSystem = codeSystem(false);
		codeSystem.setClassified(true);

		classifyJobService.addClassificationStatus(codeSystem);

		assertEquals(CodeSystemClassificationStatus.COMPLETE, codeSystem.getClassificationStatus());
	}

	@Test
	void addClassificationStatus_notClassifiedWithoutJob_isTodo() {
		CodeSystem codeSystem = codeSystem(false);
		when(classifyJobService.getLatestJob("SNOMEDCT-TEST")).thenReturn(null);

		classifyJobService.addClassificationStatus(codeSystem);

		assertEquals(CodeSystemClassificationStatus.TODO, codeSystem.getClassificationStatus());
	}

	@Test
	void addClassificationStatus_completeJobWithoutClassifiedFlag_isTodo() {
		CodeSystem codeSystem = codeSystem(false);
		ExternalServiceJob job = new ExternalServiceJob(codeSystem, "Classification");
		job.setStatus(JobStatus.COMPLETE);
		when(classifyJobService.getLatestJob("SNOMEDCT-TEST")).thenReturn(job);

		classifyJobService.addClassificationStatus(codeSystem);

		assertEquals(CodeSystemClassificationStatus.TODO, codeSystem.getClassificationStatus());
	}

	@Test
	void addClassificationStatus_inProgressJob_isInProgress() {
		CodeSystem codeSystem = codeSystem(false);
		ExternalServiceJob job = new ExternalServiceJob(codeSystem, "Classification");
		job.setStatus(JobStatus.IN_PROGRESS);
		when(classifyJobService.getLatestJob("SNOMEDCT-TEST")).thenReturn(job);

		classifyJobService.addClassificationStatus(codeSystem);

		assertEquals(CodeSystemClassificationStatus.IN_PROGRESS, codeSystem.getClassificationStatus());
	}

	private static CodeSystem codeSystem(boolean classified) {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setClassified(classified);
		return codeSystem;
	}

}
