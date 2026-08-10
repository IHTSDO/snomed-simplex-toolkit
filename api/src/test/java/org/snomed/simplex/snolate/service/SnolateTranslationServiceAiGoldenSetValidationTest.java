package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnolateTranslationServiceAiGoldenSetValidationTest {

	@Test
	void validateAiGoldenSetSize_allowsUpToMax() {
		assertThatCode(() -> SnolateTranslationService.validateAiGoldenSetSize(buildGoldenSet(40)))
				.doesNotThrowAnyException();
	}

	@Test
	void validateAiGoldenSetSize_allowsNull() {
		assertThatCode(() -> SnolateTranslationService.validateAiGoldenSetSize(null))
				.doesNotThrowAnyException();
	}

	@Test
	void validateAiGoldenSetSize_rejectsOverMax() {
		assertThatThrownBy(() -> SnolateTranslationService.validateAiGoldenSetSize(buildGoldenSet(41)))
				.isInstanceOf(ServiceExceptionWithStatusCode.class)
				.hasMessage("Golden examples cannot exceed 40.")
				.satisfies(ex -> assertThat(((ServiceExceptionWithStatusCode) ex).getStatusCode())
						.isEqualTo(HttpStatus.BAD_REQUEST.value()));
	}

	private static Map<String, String> buildGoldenSet(int size) {
		Map<String, String> goldenSet = new LinkedHashMap<>();
		for (int i = 0; i < size; i++) {
			goldenSet.put("%d|Term %d".formatted(i, i), "Translation %d".formatted(i));
		}
		return goldenSet;
	}
}
