package org.snomed.simplex.service;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.job.ContentJob;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SimpleMapSpreadsheetUploadTestSupport {

	static final Set<String> EXISTING_SNOMED_CONCEPT_IDS = Set.of("404684003", "1148601009");
	static final String REFSET_ID = "999888777001";
	static final String DEFAULT_MODULE = "900000000000207008";

	static final String TO_SNOMED_FIXTURE = "/maps/to-snomed-mapSpreadsheet.xlsx";
	static final String FROM_SNOMED_FIXTURE = "/maps/from-snomed-mapSpreadsheet.xlsx";

	private SimpleMapSpreadsheetUploadTestSupport() {
	}

	static CodeSystem testCodeSystem() {
		CodeSystem codeSystem = new CodeSystem("Test edition", "TEST", "MAIN");
		codeSystem.setDefaultModule(DEFAULT_MODULE);
		return codeSystem;
	}

	static SnowstormClientFactory snowstormClientFactory(SnowstormClient snowstormClient) throws ServiceExceptionWithStatusCode {
		SnowstormClientFactory factory = mock(SnowstormClientFactory.class);
		when(factory.getClient()).thenReturn(snowstormClient);
		return factory;
	}

	static SnowstormClient mockSnowstormClientForUpload() throws ServiceException, ServiceExceptionWithStatusCode {
		SnowstormClient snowstormClient = mock(SnowstormClient.class);
		when(snowstormClient.getRefsetOrThrow(eq(REFSET_ID), any(CodeSystem.class)))
				.thenReturn(new ConceptMini(REFSET_ID, null));
		when(snowstormClient.loadAllRefsetMembers(eq(REFSET_ID), any(CodeSystem.class), eq(false)))
				.thenReturn(List.of());
		when(snowstormClient.countAllActiveRefsetMembers(eq(REFSET_ID), any(CodeSystem.class)))
				.thenReturn(2);
		when(snowstormClient.getConceptIds(any(), any(CodeSystem.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Collection<String> conceptIds = invocation.getArgument(0);
			return conceptIds.stream()
					.filter(EXISTING_SNOMED_CONCEPT_IDS::contains)
					.map(Long::parseLong)
					.toList();
		});
		return snowstormClient;
	}

	static ContentJob uploadJob(Class<?> testClass, CodeSystem codeSystem, String resourcePath) throws IOException {
		InputStream inputStream = testClass.getResourceAsStream(resourcePath);
		assertNotNull(inputStream, "Missing test resource " + resourcePath);
		ContentJob contentJob = new ContentJob(codeSystem, "Upload test", REFSET_ID);
		contentJob.addUpload(inputStream, resourcePath);
		return contentJob;
	}
}
