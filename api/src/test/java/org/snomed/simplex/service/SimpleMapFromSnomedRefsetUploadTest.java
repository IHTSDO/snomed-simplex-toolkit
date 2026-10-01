package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.snomed.simplex.service.SimpleMapSpreadsheetUploadTestSupport.*;

@ExtendWith(MockitoExtension.class)
class SimpleMapFromSnomedRefsetUploadTest {

	private SpreadsheetService spreadsheetService;
	private SnowstormClient snowstormClient;
	private SimpleMapFromSnomedRefsetService service;
	private CodeSystem codeSystem;

	@BeforeEach
	void setUp() throws Exception {
		spreadsheetService = new SpreadsheetService();
		snowstormClient = mockSnowstormClientForUpload();
		SnowstormClientFactory factory = snowstormClientFactory(snowstormClient);
		service = new SimpleMapFromSnomedRefsetService(spreadsheetService, factory);
		codeSystem = testCodeSystem();
	}

	@Test
	void uploadCorrectSpreadsheet() throws Exception {
		ContentJob contentJob = uploadJob(getClass(), codeSystem, FROM_SNOMED_FIXTURE);

		ChangeSummary summary = service.updateRefsetViaSpreadsheet(contentJob);

		assertEquals(2, summary.getAdded());
		ArgumentCaptor<List<RefsetMember>> captor = ArgumentCaptor.forClass(List.class);
		verify(snowstormClient).createUpdateRefsetMembers(captor.capture(), any(CodeSystem.class));
		List<RefsetMember> members = captor.getValue();
		assertEquals(2, members.size());

		Map<String, String> sourceToTarget = members.stream().collect(Collectors.toMap(
				RefsetMember::getReferencedComponentId,
				m -> m.getAdditionalFields().get(SimpleMapServiceHelper.MAP_TARGET)));
		assertEquals("AA", sourceToTarget.get("404684003"));
		assertEquals("BB", sourceToTarget.get("1148601009"));
	}

	@Test
	void uploadWrongSpreadsheet() throws Exception {
		ContentJob contentJob = uploadJob(getClass(), codeSystem, TO_SNOMED_FIXTURE);

		ChangeSummary summary = service.updateRefsetViaSpreadsheet(contentJob);

		assertEquals(0, summary.getAdded());
		verify(snowstormClient, never()).createUpdateRefsetMembers(any(), any(CodeSystem.class));
	}
}
