package org.snomed.simplex.client.rvf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ValidationReportKnownFalsePositiveFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidationReportKnownFalsePositiveFilter.class);

	public record FullComponentFalsePositiveRule(String assertionUuid, List<String> requiredSubstrings) {
	}

	private ValidationReportKnownFalsePositiveFilter() {
	}

	public static List<FullComponentFalsePositiveRule> parseRules(String config) {
		if (config == null || config.isBlank()) {
			return List.of();
		}
		List<FullComponentFalsePositiveRule> rules = new ArrayList<>();
		for (String ruleSegment : config.split(";")) {
			parseRuleSegment(ruleSegment.trim()).ifPresent(rules::add);
		}
		return List.copyOf(rules);
	}

	private static Optional<FullComponentFalsePositiveRule> parseRuleSegment(String trimmed) {
		if (trimmed.isEmpty()) {
			return Optional.empty();
		}
		String[] parts = trimmed.split("\\|", -1);
		if (parts.length < 2) {
			LOGGER.warn("Skipping invalid RVF false-positive rule (expected assertionUuid|substring...): {}", trimmed);
			return Optional.empty();
		}
		String assertionUuid = parts[0].trim();
		if (assertionUuid.isEmpty()) {
			LOGGER.warn("Skipping RVF false-positive rule with blank assertion UUID: {}", trimmed);
			return Optional.empty();
		}
		List<String> substrings = Arrays.stream(parts, 1, parts.length)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
		if (substrings.isEmpty()) {
			LOGGER.warn("Skipping RVF false-positive rule with no fullComponent substrings: {}", trimmed);
			return Optional.empty();
		}
		return Optional.of(new FullComponentFalsePositiveRule(assertionUuid, substrings));
	}

	public static ValidationReport apply(ValidationReport report, List<FullComponentFalsePositiveRule> rules) {
		if (rules.isEmpty() || report == null || report.rvfValidationResult() == null) {
			return report;
		}
		ValidationReport.ValidationResult validationResult = report.rvfValidationResult();
		ValidationReport.TestResult testResult = validationResult.TestResult();
		if (testResult == null) {
			return report;
		}
		List<ValidationReport.Assertion> filteredFailed = filterAssertions(testResult.assertionsFailed(), rules);
		List<ValidationReport.Assertion> filteredWarning = filterAssertions(testResult.assertionsWarning(), rules);
		int totalFailures = sumFailureCounts(filteredFailed);
		int totalWarnings = sumFailureCounts(filteredWarning);
		ValidationReport.TestResult filteredTestResult = new ValidationReport.TestResult(
				testResult.totalTestsRun(),
				totalWarnings,
				totalFailures,
				filteredFailed,
				filteredWarning);
		ValidationReport.ValidationResult filteredValidationResult = new ValidationReport.ValidationResult(
				validationResult.validationConfig(),
				filteredTestResult,
				validationResult.startTime(),
				validationResult.endTime());
		return new ValidationReport(report.status(), report.Message(), filteredValidationResult);
	}

	private static int sumFailureCounts(List<ValidationReport.Assertion> assertions) {
		if (assertions == null) {
			return 0;
		}
		return assertions.stream().mapToInt(ValidationReport.Assertion::failureCount).sum();
	}

	private static List<ValidationReport.Assertion> filterAssertions(List<ValidationReport.Assertion> assertions,
			List<FullComponentFalsePositiveRule> rules) {
		if (assertions == null || assertions.isEmpty()) {
			return assertions;
		}
		return assertions.stream()
				.map(assertion -> filterAssertion(assertion, rules))
				.filter(Objects::nonNull)
				.toList();
	}

	private static ValidationReport.Assertion filterAssertion(ValidationReport.Assertion assertion,
			List<FullComponentFalsePositiveRule> rules) {
		List<FullComponentFalsePositiveRule> matchingRules = rules.stream()
				.filter(rule -> rule.assertionUuid().equals(assertion.assertionUuid()))
				.toList();
		if (matchingRules.isEmpty() || assertion.firstNInstances() == null || assertion.firstNInstances().isEmpty()) {
			return assertion;
		}
		List<ValidationReport.AssertionIssue> keptInstances = new ArrayList<>();
		int removedCount = 0;
		for (ValidationReport.AssertionIssue issue : assertion.firstNInstances()) {
			if (shouldExclude(issue, matchingRules)) {
				removedCount++;
			} else {
				keptInstances.add(issue);
			}
		}
		if (removedCount == 0) {
			return assertion;
		}
		int adjustedFailureCount = assertion.failureCount() - removedCount;
		if (adjustedFailureCount <= 0 || keptInstances.isEmpty()) {
			return null;
		}
		return new ValidationReport.Assertion(
				assertion.testCategory(),
				assertion.testType(),
				assertion.assertionUuid(),
				assertion.assertionText(),
				adjustedFailureCount,
				keptInstances);
	}

	private static boolean shouldExclude(ValidationReport.AssertionIssue issue, List<FullComponentFalsePositiveRule> matchingRules) {
		String fullComponent = issue.fullComponent();
		if (fullComponent == null || fullComponent.isBlank()) {
			return false;
		}
		for (FullComponentFalsePositiveRule rule : matchingRules) {
			if (rule.requiredSubstrings().stream().allMatch(fullComponent::contains)) {
				return true;
			}
		}
		return false;
	}
}
