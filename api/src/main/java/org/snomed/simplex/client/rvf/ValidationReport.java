package org.snomed.simplex.client.rvf;

import java.util.List;

public record ValidationReport(
		State status,
		String Message,
		ValidationResult rvfValidationResult) {

	public enum State { QUEUED, READY, RUNNING, FAILED, COMPLETE }

	public record ValidationResult(
			ValidationReport.TestResult TestResult) {
	}

	public record TestResult(
			int totalTestsRun,
			int totalWarnings,
			int totalFailures,
			List<ValidationReport.Assertion> assertionsFailed,
			List<ValidationReport.Assertion> assertionsWarning) {

	}

	public record Assertion(
			String testCategory,
			String testType,
			String assertionUuid,
			String assertionText,
			int failureCount,
			List<ValidationReport.AssertionIssue> firstNInstances) {

	}

	public record AssertionIssue(
			String conceptId,
			String conceptFsn,
			String componentId,
			String detail,
			String fullComponent) {

	}
}
