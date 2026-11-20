package org.snomed.simplex.translation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.service.EditionEventService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.snolate.service.TranslationStudioImportJobRecordService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedServiceBranchEventListenerTest {

	private static final String MESSAGE = """
			[{"event": "%s", "branch": {"branchPath": "%s", "sourceBranchPath": "MAIN", "labels": "translation"}}]""";

	@Mock
	private ManagedServiceTaskEventService taskEventService;
	@Mock
	private SnowstormClientFactory snowstormClientFactory;
	@Mock
	private SnowstormClient snowstormClient;
	@Mock
	private AuthenticationClient authenticationClient;
	@Mock
	private TranslationStudioImportJobRecordService jobRecordService;
	@Mock
	private EditionEventService editionEventService;

	private ManagedServiceBranchEventListener listener;
	private final CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");

	@BeforeEach
	void setUp() throws Exception {
		listener = new ManagedServiceBranchEventListener(taskEventService, snowstormClientFactory, authenticationClient,
				new ObjectMapper(), jobRecordService, editionEventService, "service", "secret", "");
	}

	private void knownCodeSystem() throws Exception {
		when(authenticationClient.fetchAuthenticationToken("service", "secret")).thenReturn("token");
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystems(false)).thenReturn(List.of(codeSystem));
		lenient().when(taskEventService.pullTranslationContentFromSnowstorm(codeSystem, snowstormClient))
				.thenReturn(new ManagedServiceTaskEventService.PullResult(1, List.of()));
	}

	@Test
	void rebasePullsContentFromSnowstorm() throws Exception {
		knownCodeSystem();

		listener.receiveBranchEvents(MESSAGE.formatted("REBASE", "MAIN/SNOMEDCT-TEST/translation"));

		verify(taskEventService).pullTranslationContentFromSnowstorm(codeSystem, snowstormClient);
		ArgumentCaptor<AsyncJob> job = ArgumentCaptor.forClass(AsyncJob.class);
		verify(jobRecordService).saveFromJob(job.capture());
		assertEquals(JobStatus.COMPLETE, job.getValue().getStatus());
		Map<String, Object> event = capturePublishedEvent(EditionEventService.EVENT_TRANSLATION_SYNC);
		assertEquals("PROJECT_REBASED", event.get("trigger"));
		assertEquals("COMPLETE", event.get("status"));
		assertFalse(event.containsKey("unitsCompleted"));
	}

	@Test
	void failedRefsetSyncIsRecordedAsSystemError() throws Exception {
		knownCodeSystem();
		when(taskEventService.pullTranslationContentFromSnowstorm(codeSystem, snowstormClient))
				.thenReturn(new ManagedServiceTaskEventService.PullResult(0, List.of("123: boom")));

		listener.receiveBranchEvents(MESSAGE.formatted("REBASE", "MAIN/SNOMEDCT-TEST/translation"));

		ArgumentCaptor<AsyncJob> job = ArgumentCaptor.forClass(AsyncJob.class);
		verify(jobRecordService).saveFromJob(job.capture());
		assertEquals(JobStatus.SYSTEM_ERROR, job.getValue().getStatus());
		assertTrue(job.getValue().getErrorMessage().contains("123: boom"));
	}

	@Test
	void promotionTransitionsUnitsToPromotedAndPullsProjectContent() throws Exception {
		knownCodeSystem();

		when(taskEventService.handleTaskPromoted("SNOMEDCT-TEST")).thenReturn(9);

		listener.receiveBranchEvents(MESSAGE.formatted("PROMOTE", "MAIN/SNOMEDCT-TEST/translation"));

		verify(taskEventService).handleTaskPromoted("SNOMEDCT-TEST");
		verify(taskEventService).pullTranslationContentFromSnowstorm(codeSystem, snowstormClient);
		Map<String, Object> event = capturePublishedEvent(EditionEventService.EVENT_TRANSLATION_SYNC);
		assertEquals("TASK_PROMOTED", event.get("trigger"));
		assertEquals(9, event.get("unitsCompleted"));
	}

	@Test
	void taskDeletionRevertsUnitsUsingTheProjectBranchOfTheTask() throws Exception {
		knownCodeSystem();

		listener.receiveBranchEvents(MESSAGE.formatted("DELETE", "MAIN/SNOMEDCT-TEST/translation/translation-12"));

		verify(taskEventService).handleTaskRejected("SNOMEDCT-TEST");
		Map<String, Object> event = capturePublishedEvent(EditionEventService.EVENT_TRANSLATION_TASK_REVERTED);
		assertEquals("MAIN/SNOMEDCT-TEST/translation/translation-12", event.get("taskBranch"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> capturePublishedEvent(String eventName) {
		ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
		verify(editionEventService).publish(eq("SNOMEDCT-TEST"), eq(eventName), data.capture());
		return (Map<String, Object>) data.getValue();
	}

	@Test
	void unknownCodeSystemIsIgnored() throws Exception {
		knownCodeSystem();

		listener.receiveBranchEvents(MESSAGE.formatted("PROMOTE", "MAIN/OTHER/translation"));

		verify(taskEventService, never()).handleTaskPromoted(any());
	}

	@Test
	void ignoredWhenServiceAccountNotConfigured() {
		listener = new ManagedServiceBranchEventListener(taskEventService, snowstormClientFactory, authenticationClient,
				new ObjectMapper(), jobRecordService, editionEventService, "", "", "");

		listener.receiveBranchEvents(MESSAGE.formatted("PROMOTE", "MAIN/SNOMEDCT-TEST/translation"));

		verifyNoInteractions(taskEventService, authenticationClient);
	}

	@Test
	void devWorkaroundTokenUsedWhenNoUsernameAndPassword() throws Exception {
		listener = new ManagedServiceBranchEventListener(taskEventService, snowstormClientFactory, authenticationClient,
				new ObjectMapper(), jobRecordService, editionEventService, "", "", "ims-token");
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystems(false)).thenReturn(List.of(codeSystem));

		listener.receiveBranchEvents(MESSAGE.formatted("PROMOTE", "MAIN/SNOMEDCT-TEST/translation"));

		verify(taskEventService).handleTaskPromoted("SNOMEDCT-TEST");
		verifyNoInteractions(authenticationClient);
	}

	@Test
	void malformedMessageIsIgnored() {
		listener.receiveBranchEvents("not json");

		verifyNoInteractions(taskEventService);
	}
}
