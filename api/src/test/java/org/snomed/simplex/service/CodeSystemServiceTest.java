package org.snomed.simplex.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.TestConcepts;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CreateCodeSystemRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CodeSystemServiceTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@MockitoBean
	private SnowstormClientFactory snowstormClientFactory;

	@Test
	void testValidateCreateRequest() throws ServiceExceptionWithStatusCode {
		try {
			codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName(""));
		} catch (ServiceExceptionWithStatusCode e) {
			assertEquals("CodeSystem short name must start with 'SNOMEDCT-'", e.getMessage());
		}
		try {
			codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT"));
		} catch (ServiceExceptionWithStatusCode e) {
			assertEquals("CodeSystem short name must start with 'SNOMEDCT-'", e.getMessage());
		}
		try {
			codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("ABC"));
		} catch (ServiceExceptionWithStatusCode e) {
			assertEquals("CodeSystem short name must start with 'SNOMEDCT-'", e.getMessage());
		}

		try {
			codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT-"));
		} catch (ServiceExceptionWithStatusCode e) {
			assertEquals("CodeSystem short name must start with 'SNOMEDCT-' and contain other characters.", e.getMessage());
		}
		try {
			codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT-ABc"));
		} catch (ServiceExceptionWithStatusCode e) {
			assertEquals("CodeSystem short name can only contain characters A-Z, 0-9, hyphen and underscore.", e.getMessage());
		}

		codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT-A"));
		codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT-ABC"));
		codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT-THIS-ONE-IS-OKAY_BECAUSE-LESS-THAN-70-CHARS"));

		try {
			codeSystemService.validateCreateRequest(new CreateCodeSystemRequest().setShortName("SNOMEDCT-THIS-ONE-IS-NOT-OKAY_THIS-IS-FAR-FAR-TOO-CRAZY-LONGGGGGGGGGGGGG"));
		} catch (ServiceExceptionWithStatusCode e) {
			assertEquals("CodeSystem short name max length exceeded. Maximum length is 70 characters.", e.getMessage());
		}
	}

	@Test
	void testGetUserGroupName() {
		assertEquals("simplex-cz-author", codeSystemService.getUserGroupName("SNOMEDCT-CZ"));
		assertEquals("simplex-aa-defence-author", codeSystemService.getUserGroupName("SNOMEDCT-AA-DEFENCE"));
		assertEquals("simplex-north-west_vendor-author", codeSystemService.getUserGroupName("SNOMEDCT-NORTH-WEST_VENDOR"));
	}

	@Test
	void testProcessMDRSRowsDuplicates() {
		List<RefsetMember> mdrsRows = new ArrayList<>();
		mdrsRows.add(createMDRSRow(Concepts.CORE_MODULE, "20240310", "20240101"));
		mdrsRows.add(createMDRSRow(Concepts.CORE_MODULE, "20251015", "20251001"));
		mdrsRows.add(createMDRSRow(Concepts.CORE_MODULE, "20250810", "20250701"));
		mdrsRows.add(createMDRSRow(Concepts.MODEL_MODULE, "20240310", "20240101"));
		mdrsRows.add(createMDRSRow(Concepts.MODEL_MODULE, "20251015", "20251001"));
		mdrsRows.add(createMDRSRow(Concepts.MODEL_MODULE, "20250810", "20250701"));

		assertEquals(6, mdrsRows.size());
		for (RefsetMember member : mdrsRows) {
			assertTrue(member.isActive());
		}

		codeSystemService.processMDRSRows(mdrsRows, TestConcepts.MODULE, 20251101);

		assertEquals(6, mdrsRows.size());
		List<RefsetMember> activeRows = mdrsRows.stream().filter(RefsetMember::isActive).toList();
		assertEquals(2, activeRows.size());
		assertEquals("", activeRows.get(0).getAdditionalFields().get(CodeSystemService.SOURCE_EFFECTIVE_TIME));
		assertEquals("20251101", activeRows.get(0).getAdditionalFields().get(CodeSystemService.TARGET_EFFECTIVE_TIME));
		assertEquals("", activeRows.get(1).getAdditionalFields().get(CodeSystemService.SOURCE_EFFECTIVE_TIME));
		assertEquals("20251101", activeRows.get(1).getAdditionalFields().get(CodeSystemService.TARGET_EFFECTIVE_TIME));
	}

	@Test
	void testProcessMDRSRowsNew() {
		List<RefsetMember> mdrsRows = new ArrayList<>();

		codeSystemService.processMDRSRows(mdrsRows, TestConcepts.MODULE, 20250101);

		assertEquals(2, mdrsRows.size());
		List<RefsetMember> activeRows = mdrsRows.stream().filter(RefsetMember::isActive).toList();
		assertEquals(2, activeRows.size());
		assertEquals("", activeRows.get(0).getAdditionalFields().get(CodeSystemService.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", activeRows.get(0).getAdditionalFields().get(CodeSystemService.TARGET_EFFECTIVE_TIME));
		assertEquals("", activeRows.get(1).getAdditionalFields().get(CodeSystemService.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", activeRows.get(1).getAdditionalFields().get(CodeSystemService.TARGET_EFFECTIVE_TIME));
	}

	private static RefsetMember createMDRSRow(String targetModule, String sourceDate, String targetDate) {
		return new RefsetMember(Concepts.MODULE_DEPENDENCY_REFERENCE_SET, TestConcepts.MODULE, targetModule)
			.setAdditionalField(CodeSystemService.SOURCE_EFFECTIVE_TIME, sourceDate)
			.setAdditionalField(CodeSystemService.TARGET_EFFECTIVE_TIME, targetDate);
	}
}
