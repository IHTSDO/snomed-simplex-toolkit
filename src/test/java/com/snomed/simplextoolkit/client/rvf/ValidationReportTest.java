package com.snomed.simplextoolkit.client.rvf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationReportTest {

	@Test
	public void testDeserialization() throws IOException {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

		ValidationReport report = objectMapper.readValue(getClass().getResourceAsStream("/rvf-report-example.json"), ValidationReport.class);

		assertEquals(ValidationReport.State.COMPLETE, report.status());

		ValidationReport.ValidationResult validationResult = report.rvfValidationResult();
		assertNotNull(validationResult);

		ValidationReport.TestResult testResult = validationResult.TestResult();
		assertNotNull(testResult);
		assertEquals(930, testResult.totalTestsRun());

		List<ValidationReport.Assertion> assertionWarnings = testResult.assertionsWarning();
		assertNotNull(assertionWarnings);
		assertEquals(2, assertionWarnings.size());

		ValidationReport.Assertion assertionWarning = assertionWarnings.get(0);
		assertEquals("d007641a-a124-4096-84fe-d2e09dcb7f40", assertionWarning.assertionUuid());
		assertEquals(1120, assertionWarning.failureCount());

		List<ValidationReport.AssertionIssue> warningInstances = assertionWarning.firstNInstances();
		assertEquals(100, warningInstances.size());

		ValidationReport.AssertionIssue warningInstance = warningInstances.get(0);
		assertEquals("386692008", warningInstance.conceptId());
		assertEquals("360261000172118", warningInstance.componentId());
		assertEquals("Active terms sharing first word with case-sensitive term should share case sensitivity.", warningInstance.detail());
	}

}