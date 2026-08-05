package org.snomed.simplex.client.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSystemBuildStatusTest {

	@ParameterizedTest
	@CsvSource({
			"FAILED_PRE_CONDITIONS, FAILED",
			"failed_pre_conditions, FAILED",
			"FAILED_INPUT_GATHER_REPORT_VALIDATION, FAILED",
			"FAILED_INPUT_PREPARE_REPORT_VALIDATION, FAILED",
			"FAILED, FAILED",
			"RVF_FAILED, FAILED",
			"FAILED_POST_CONDITIONS, FAILED",
			"CANCELLED, FAILED",
			"PENDING, IN_PROGRESS",
			"QUEUED, IN_PROGRESS",
			"BEFORE_TRIGGER, IN_PROGRESS",
			"BUILDING, IN_PROGRESS",
			"BUILT, IN_PROGRESS",
			"RVF_QUEUED, IN_PROGRESS",
			"RVF_RUNNING, IN_PROGRESS",
			"CANCEL_REQUESTED, IN_PROGRESS",
			"UNKNOWN, IN_PROGRESS",
			"RELEASE_COMPLETE, COMPLETE",
			"RELEASE_COMPLETE_WITH_WARNINGS, COMPLETE"
	})
	void fromSRSStatus(String srsStatus, CodeSystemBuildStatus expected) {
		assertEquals(expected, CodeSystemBuildStatus.fromSRSStatus(srsStatus));
	}

	@Test
	void fromSRSStatus_nullOrBlank_returnsTodo() {
		assertEquals(CodeSystemBuildStatus.TODO, CodeSystemBuildStatus.fromSRSStatus(null));
		assertEquals(CodeSystemBuildStatus.TODO, CodeSystemBuildStatus.fromSRSStatus(""));
		assertEquals(CodeSystemBuildStatus.TODO, CodeSystemBuildStatus.fromSRSStatus("   "));
	}

	@Test
	void fromSRSStatus_unknownStatus_returnsFailed() {
		assertEquals(CodeSystemBuildStatus.FAILED, CodeSystemBuildStatus.fromSRSStatus("SOME_NEW_TERMINAL_STATUS"));
	}

	@Test
	void isTerminal() {
		assertFalse(CodeSystemBuildStatus.TODO.isTerminal());
		assertFalse(CodeSystemBuildStatus.IN_PROGRESS.isTerminal());
		assertTrue(CodeSystemBuildStatus.FAILED.isTerminal());
		assertTrue(CodeSystemBuildStatus.COMPLETE.isTerminal());
	}
}
