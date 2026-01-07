package org.snomed.simplex.translation.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.translation.domain.TranslationState;

public interface TranslationSource {

	TranslationState readTranslation() throws ServiceExceptionWithStatusCode;

	void writeTranslation(TranslationState translationState) throws ServiceExceptionWithStatusCode;

	TranslationSourceType getType();

}
