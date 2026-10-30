package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LanguageRefsetInactiveDescriptionCleanupResult;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageRefsetInactiveDescriptionCleanupServiceTest {

	private static final String REFSET_ID = "123000000000000000";
	private static final String MODULE = "101000003010";

	@Mock
	private SnowstormClient snowstormClient;

	@Mock
	private TranslationService translationService;

	private LanguageRefsetInactiveDescriptionCleanupService service;

	private CodeSystem codeSystem;

	@BeforeEach
	void setUp() {
		service = new LanguageRefsetInactiveDescriptionCleanupService(translationService);
		codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
	}

	@Test
	void cleanup_skipsMemberWhenDescriptionIsActive() throws Exception {
		RefsetMember member = member("1001");
		stubSinglePage(List.of(member));
		when(snowstormClient.loadDescriptionActiveFlags(List.of("1001"), codeSystem)).thenReturn(Map.of("1001", true));
		when(snowstormClient.countAllActiveRefsetMembers(REFSET_ID, codeSystem)).thenReturn(1);

		LanguageRefsetInactiveDescriptionCleanupResult result = service.cleanup(codeSystem, snowstormClient, REFSET_ID);

		assertEquals(1, result.activeMembersScanned());
		assertEquals(0, result.membersInactivated());
		assertEquals(1, result.activeMembersRemaining());
		verify(snowstormClient, never()).createUpdateRefsetMembers(any(), any());
		verify(translationService).clearLanguageRefsetCache(codeSystem.getShortName());
	}

	@Test
	void cleanup_inactivatesMemberWhenDescriptionIsInactive() throws Exception {
		RefsetMember member = member("1001");
		stubSinglePage(List.of(member));
		when(snowstormClient.loadDescriptionActiveFlags(List.of("1001"), codeSystem)).thenReturn(Map.of("1001", false));
		when(snowstormClient.countAllActiveRefsetMembers(REFSET_ID, codeSystem)).thenReturn(0);

		LanguageRefsetInactiveDescriptionCleanupResult result = service.cleanup(codeSystem, snowstormClient, REFSET_ID);

		assertEquals(1, result.activeMembersScanned());
		assertEquals(1, result.membersInactivated());
		ArgumentCaptor<List<RefsetMember>> captor = ArgumentCaptor.forClass(List.class);
		verify(snowstormClient).createUpdateRefsetMembers(captor.capture(), eq(codeSystem));
		assertFalse(captor.getValue().get(0).isActive());
	}

	@Test
	void cleanup_inactivatesMemberWhenDescriptionIsMissingFromBulkLoad() throws Exception {
		RefsetMember member = member("1001");
		stubSinglePage(List.of(member));
		when(snowstormClient.loadDescriptionActiveFlags(List.of("1001"), codeSystem)).thenReturn(Map.of());
		when(snowstormClient.countAllActiveRefsetMembers(REFSET_ID, codeSystem)).thenReturn(0);

		service.cleanup(codeSystem, snowstormClient, REFSET_ID);

		verify(snowstormClient).createUpdateRefsetMembers(any(), eq(codeSystem));
	}

	@Test
	void cleanup_processesMultipleMemberPages() throws Exception {
		RefsetMember pageOneMember = member("1001");
		RefsetMember pageTwoMember = member("1002");
		when(snowstormClient.getRefsetOrThrow(REFSET_ID, codeSystem)).thenReturn(new ConceptMini(REFSET_ID, null));
		doAnswer(invocation -> {
			SnowstormClient.RefsetMemberPageHandler handler = invocation.getArgument(3);
			handler.accept(List.of(pageOneMember));
			handler.accept(List.of(pageTwoMember));
			return null;
		}).when(snowstormClient).forEachRefsetMemberPage(eq(REFSET_ID), eq(codeSystem), eq(true), any());
		when(snowstormClient.loadDescriptionActiveFlags(List.of("1001"), codeSystem)).thenReturn(Map.of("1001", true));
		when(snowstormClient.loadDescriptionActiveFlags(List.of("1002"), codeSystem)).thenReturn(Map.of("1002", false));
		when(snowstormClient.countAllActiveRefsetMembers(REFSET_ID, codeSystem)).thenReturn(1);

		LanguageRefsetInactiveDescriptionCleanupResult result = service.cleanup(codeSystem, snowstormClient, REFSET_ID);

		assertEquals(2, result.activeMembersScanned());
		assertEquals(1, result.membersInactivated());
		verify(snowstormClient).loadDescriptionActiveFlags(List.of("1001"), codeSystem);
		verify(snowstormClient).loadDescriptionActiveFlags(List.of("1002"), codeSystem);
	}

	@Test
	void cleanup_rejectsFoundationEnglishLangRefset() {
		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> service.cleanup(codeSystem, snowstormClient, Concepts.US_LANG_REFSET));
		assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode());
	}

	private void stubSinglePage(List<RefsetMember> members) throws Exception {
		when(snowstormClient.getRefsetOrThrow(REFSET_ID, codeSystem)).thenReturn(new ConceptMini(REFSET_ID, null));
		doAnswer(invocation -> {
			SnowstormClient.RefsetMemberPageHandler handler = invocation.getArgument(3);
			handler.accept(members);
			return null;
		}).when(snowstormClient).forEachRefsetMemberPage(eq(REFSET_ID), eq(codeSystem), eq(true), any());
	}

	private static RefsetMember member(String descriptionId) {
		return new RefsetMember(REFSET_ID, MODULE, descriptionId);
	}
}
