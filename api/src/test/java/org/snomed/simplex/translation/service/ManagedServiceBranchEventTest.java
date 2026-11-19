package org.snomed.simplex.translation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.translation.service.ManagedServiceBranchEvent.Type;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedServiceBranchEventTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private List<ManagedServiceBranchEvent> parse(String json) throws Exception {
		return ManagedServiceBranchEvent.parse(objectMapper.readTree(json));
	}

	@Test
	void parsesWrappedEventList() throws Exception {
		List<ManagedServiceBranchEvent> events = parse("""
				[
				  {"event": "REBASE", "branch": {"commitType": "REBASE", "branchPath": "MAIN/X", "sourceBranchPath": "MAIN", "labels": "translation"}},
				  {"event": "PROMOTE", "branch": {"commitType": "PROMOTION", "branchPath": "MAIN/X", "sourceBranchPath": "MAIN/X/X-1", "labels": "translation"}},
				  {"event": "DELETE", "branch": {"branchPath": "MAIN/X/X-2", "labels": "translation"}}
				]""");

		assertThat(events).containsExactly(
				new ManagedServiceBranchEvent(Type.REBASE, "MAIN/X", "MAIN"),
				new ManagedServiceBranchEvent(Type.PROMOTE, "MAIN/X", "MAIN/X/X-1"),
				new ManagedServiceBranchEvent(Type.DELETE, "MAIN/X/X-2", null));
	}

	@Test
	void parsesDeployedFlatFormat() throws Exception {
		List<ManagedServiceBranchEvent> events = parse("""
				{"commitType": "PROMOTION", "branchPath": "MAIN/X", "sourceBranchPath": "MAIN/X/X-1", "labels": "translation"}""");

		assertThat(events).containsExactly(new ManagedServiceBranchEvent(Type.PROMOTE, "MAIN/X", "MAIN/X/X-1"));
	}

	@Test
	void ignoresNonTranslationUnknownAndMalformedEvents() throws Exception {
		List<ManagedServiceBranchEvent> events = parse("""
				[
				  {"event": "PROMOTE", "branch": {"branchPath": "MAIN/Y", "sourceBranchPath": "MAIN/Y/Y-1", "labels": "other"}},
				  {"event": "PROMOTE", "branch": {"branchPath": "MAIN/Y", "sourceBranchPath": "MAIN/Y/Y-1"}},
				  {"event": "SOMETHING", "branch": {"branchPath": "MAIN/Y", "labels": "translation"}},
				  {"event": "DELETE", "branch": {"labels": "translation"}}
				]""");

		assertThat(events).isEmpty();
	}
}
