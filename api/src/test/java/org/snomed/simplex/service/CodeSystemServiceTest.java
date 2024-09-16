package org.snomed.simplex.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CreateCodeSystemRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class CodeSystemServiceTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@MockBean
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
}
