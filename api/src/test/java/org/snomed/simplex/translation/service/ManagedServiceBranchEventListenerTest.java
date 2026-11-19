package org.snomed.simplex.translation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

	private ManagedServiceBranchEventListener listener;
	private final CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");

	@BeforeEach
	void setUp() throws Exception {
		listener = new ManagedServiceBranchEventListener(taskEventService, snowstormClientFactory, authenticationClient,
				new ObjectMapper(), "service", "secret");
	}

	private void knownCodeSystem() throws Exception {
		when(authenticationClient.fetchAuthenticationToken("service", "secret")).thenReturn("token");
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystems(false)).thenReturn(List.of(codeSystem));
	}

	@Test
	void rebasePullsContentFromSnowstorm() throws Exception {
		knownCodeSystem();

		listener.receiveBranchEvents(MESSAGE.formatted("REBASE", "MAIN/SNOMEDCT-TEST/translation"));

		verify(taskEventService).handleProjectRebased(codeSystem, snowstormClient);
	}

	@Test
	void promotionTransitionsUnitsToPromoted() throws Exception {
		knownCodeSystem();

		listener.receiveBranchEvents(MESSAGE.formatted("PROMOTE", "MAIN/SNOMEDCT-TEST/translation"));

		verify(taskEventService).handleTaskPromoted("SNOMEDCT-TEST");
	}

	@Test
	void taskDeletionRevertsUnitsUsingTheProjectBranchOfTheTask() throws Exception {
		knownCodeSystem();

		listener.receiveBranchEvents(MESSAGE.formatted("DELETE", "MAIN/SNOMEDCT-TEST/translation/translation-12"));

		verify(taskEventService).handleTaskRejected("SNOMEDCT-TEST");
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
				new ObjectMapper(), "", "");

		listener.receiveBranchEvents(MESSAGE.formatted("PROMOTE", "MAIN/SNOMEDCT-TEST/translation"));

		verifyNoInteractions(taskEventService, authenticationClient);
	}

	@Test
	void malformedMessageIsIgnored() {
		listener.receiveBranchEvents("not json");

		verifyNoInteractions(taskEventService);
	}
}
