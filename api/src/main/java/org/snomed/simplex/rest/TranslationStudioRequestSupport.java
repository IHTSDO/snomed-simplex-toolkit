package org.snomed.simplex.rest;

import org.jspecify.annotations.NonNull;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.service.OutsideSetBehavior;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

class TranslationStudioRequestSupport {

	static final int MAX_DESCRIPTION_LENGTH = 2000;

	private TranslationStudioRequestSupport() {
	}

	static void rejectFoundationEnglishLangRefset(String refsetId) throws ServiceExceptionWithStatusCode {
		if (TranslationService.isFoundationEnglishLangRefset(refsetId)) {
			throw new ServiceExceptionWithStatusCode(
					"Foundation English synonym refsets cannot be modified through this operation.", HttpStatus.BAD_REQUEST);
		}
	}

	static String normaliseDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

	static void validateDescriptionLength(String description) throws ServiceExceptionWithStatusCode {
		if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new ServiceExceptionWithStatusCode(
					"Parameter 'description' must not exceed %d characters.".formatted(MAX_DESCRIPTION_LENGTH),
					HttpStatus.BAD_REQUEST);
		}
	}

	static @NonNull List<String> getTermColumnList(List<String> termColumns) throws ServiceExceptionWithStatusCode {
		if (termColumns == null || termColumns.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("At least one term column is required.", HttpStatus.BAD_REQUEST);
		}
		if (termColumns.size() == 1 && termColumns.get(0).contains(",")) {
			return java.util.Arrays.stream(termColumns.get(0).split(","))
					.map(String::trim)
					.filter(column -> !column.isEmpty())
					.toList();
		}
		List<String> termColumnList = termColumns.stream()
				.map(String::trim)
				.filter(column -> !column.isEmpty())
				.toList();
		if (termColumnList.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("At least one term column is required.", HttpStatus.BAD_REQUEST);
		}
		return termColumnList;
	}

	static String resolveSnolateLanguageCode(CodeSystem codeSystem, String refsetId)
			throws ServiceExceptionWithStatusCode {
		Map<String, String> snolateLanguages = codeSystem.getTranslationSnolateLanguages();
		if (snolateLanguages == null || !snolateLanguages.containsKey(refsetId)) {
			throw new ServiceExceptionWithStatusCode(
					"Language is not linked to Translation Studio.", HttpStatus.BAD_REQUEST);
		}
		String languageCode = snolateLanguages.get(refsetId);
		if (languageCode == null || languageCode.isBlank()) {
			Map<String, String> translationLanguages = codeSystem.getTranslationLanguages();
			languageCode = translationLanguages != null ? translationLanguages.get(refsetId) : null;
		}
		if (languageCode == null || languageCode.isBlank()) {
			throw new ServiceExceptionWithStatusCode(
					"Language code could not be resolved for refset " + refsetId + ".", HttpStatus.BAD_REQUEST);
		}
		return languageCode;
	}

	static OutsideSetBehavior parseOutsideSetBehavior(String outsideSetBehavior)
			throws ServiceExceptionWithStatusCode {
		if (outsideSetBehavior == null || outsideSetBehavior.isBlank()) {
			return OutsideSetBehavior.SKIP;
		}
		try {
			return OutsideSetBehavior.valueOf(outsideSetBehavior.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new ServiceExceptionWithStatusCode(
					"outsideSetBehavior must be SKIP or UPDATE.", HttpStatus.BAD_REQUEST, e);
		}
	}

	static TranslationStatus parseImportTranslationStatus(String status) throws ServiceExceptionWithStatusCode {
		if (status == null || status.isBlank()) {
			throw new ServiceExceptionWithStatusCode("Import status is required.", HttpStatus.BAD_REQUEST);
		}
		try {
			TranslationStatus parsed = TranslationStatus.valueOf(status.trim());
			if (parsed == TranslationStatus.NOT_STARTED || parsed == TranslationStatus.COMPLETE) {
				throw new IllegalArgumentException("Status not allowed for import");
			}
			return parsed;
		} catch (IllegalArgumentException e) {
			throw new ServiceExceptionWithStatusCode(
					"Import status must be NEEDS_EDIT, FOR_REVIEW, or APPROVED.", HttpStatus.BAD_REQUEST, e);
		}
	}

	static TranslationStatus parseOptionalTranslationStatus(String status) throws ServiceExceptionWithStatusCode {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return TranslationStatus.valueOf(status.trim());
		} catch (IllegalArgumentException e) {
			throw new ServiceExceptionWithStatusCode("Invalid translation status.", HttpStatus.BAD_REQUEST, e);
		}
	}
}
