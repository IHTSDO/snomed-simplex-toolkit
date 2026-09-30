package org.snomed.simplex.rest;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.EditionStatus;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslationStudioRequestSupportTest {

	@Test
	void requireAuthoringEdition_authoring_doesNotThrow() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setEditionStatus(EditionStatus.AUTHORING);

		assertDoesNotThrow(() -> TranslationStudioRequestSupport.requireAuthoringEdition(codeSystem));
	}

	@Test
	void requireAuthoringEdition_preparingRelease_throwsConflict() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setEditionStatus(EditionStatus.PREPARING_RELEASE);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> TranslationStudioRequestSupport.requireAuthoringEdition(codeSystem));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
	}

	@Test
	void requireAuthoringEdition_release_throwsConflict() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setEditionStatus(EditionStatus.RELEASE);

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> TranslationStudioRequestSupport.requireAuthoringEdition(codeSystem));

		assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode());
	}

}
