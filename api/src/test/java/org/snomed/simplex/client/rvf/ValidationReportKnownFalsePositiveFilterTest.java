package org.snomed.simplex.client.rvf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.simplex.client.rvf.ValidationReportKnownFalsePositiveFilter.FullComponentFalsePositiveRule;
import static org.snomed.simplex.client.rvf.ValidationReportKnownFalsePositiveFilter.apply;
import static org.snomed.simplex.client.rvf.ValidationReportKnownFalsePositiveFilter.parseRules;

class ValidationReportKnownFalsePositiveFilterTest {

	private static final String ASSERTION_UUID = "02cf4438-170e-4b6b-b5da-624ef730a8e0";
	private static final String MATCHING_FULL_COMPONENT =
			"0,12233445107,762103008,734147008,Ontology(<http://snomed.info/sct/900000000000207008>)";

	private static final List<FullComponentFalsePositiveRule> RULES = parseRules(
			ASSERTION_UUID + "|,762103008,|Ontology(<http://snomed.info/");

	@Test
	void parseRules_parsesConfiguredRule() {
		assertEquals(1, RULES.size());
		assertEquals(ASSERTION_UUID, RULES.getFirst().assertionUuid());
		assertEquals(List.of(",762103008,", "Ontology(<http://snomed.info/"), RULES.getFirst().requiredSubstrings());
	}

	@Test
	void parseRules_skipsInvalidSegments() {
		assertTrue(parseRules("bad-rule-only").isEmpty());
		assertTrue(parseRules("|missing-assertion").isEmpty());
	}

	@Test
	void apply_removesSoleMatchingFailure() {
		ValidationReport report = reportWithFailedAssertion(ASSERTION_UUID, 1, issue(MATCHING_FULL_COMPONENT));
		ValidationReport filtered = apply(report, RULES);
		assertEquals(0, filtered.rvfValidationResult().TestResult().totalFailures());
		assertTrue(filtered.rvfValidationResult().TestResult().assertionsFailed().isEmpty());
	}

	@Test
	void apply_keepsNonMatchingInstanceOnSameAssertion() {
		ValidationReport report = reportWithFailedAssertion(ASSERTION_UUID, 2,
				issue(MATCHING_FULL_COMPONENT),
				issue("0,12233445107,999999999,734147008,Ontology(<http://snomed.info/sct/900000000000207008>)"));
		ValidationReport filtered = apply(report, RULES);
		assertEquals(1, filtered.rvfValidationResult().TestResult().totalFailures());
		assertEquals(1, filtered.rvfValidationResult().TestResult().assertionsFailed().size());
		assertEquals(1, filtered.rvfValidationResult().TestResult().assertionsFailed().getFirst().failureCount());
		assertEquals(1, filtered.rvfValidationResult().TestResult().assertionsFailed().getFirst().firstNInstances().size());
	}

	@Test
	void apply_doesNotFilterDifferentAssertion() {
		ValidationReport report = reportWithFailedAssertion("other-assertion-uuid", 1, issue(MATCHING_FULL_COMPONENT));
		ValidationReport filtered = apply(report, RULES);
		assertEquals(1, filtered.rvfValidationResult().TestResult().totalFailures());
		assertEquals(1, filtered.rvfValidationResult().TestResult().assertionsFailed().size());
	}

	@Test
	void apply_doesNotFilterWhenSubstringMissing() {
		ValidationReport report = reportWithFailedAssertion(ASSERTION_UUID, 1,
				issue("0,12233445107,762103008,734147008,no-ontology-marker"));
		ValidationReport filtered = apply(report, RULES);
		assertEquals(1, filtered.rvfValidationResult().TestResult().totalFailures());
	}

	private static ValidationReport reportWithFailedAssertion(String assertionUuid, int failureCount,
			ValidationReport.AssertionIssue... issues) {
		ValidationReport.Assertion assertion = new ValidationReport.Assertion(
				"category", "type", assertionUuid, "assertion text", failureCount, List.of(issues));
		ValidationReport.TestResult testResult = new ValidationReport.TestResult(
				1, 0, failureCount, List.of(assertion), List.of());
		ValidationReport.ValidationResult validationResult = new ValidationReport.ValidationResult(
				new ValidationReport.ValidationConfig(1L, 2L), testResult, "start", "end");
		return new ValidationReport(ValidationReport.State.COMPLETE, null, validationResult);
	}

	private static ValidationReport.AssertionIssue issue(String fullComponent) {
		return new ValidationReport.AssertionIssue("123", "FSN", "456", "detail", fullComponent);
	}
}
