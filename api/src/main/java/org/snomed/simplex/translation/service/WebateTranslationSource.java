package org.snomed.simplex.translation.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.weblate.WeblateClient;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Long.parseLong;

public class WebateTranslationSource implements TranslationSource {

	private final WeblateClient weblateClient;
	private final String compositeLanguageCode;

	public WebateTranslationSource(WeblateClient weblateClient, String languageCode, String refsetId) {
		this.weblateClient = weblateClient;
		compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
	}

	@Override
	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		try {
			File weblateFile = weblateClient.downloadTranslation(compositeLanguageCode);
			return fileToTranslationState(weblateFile);
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to download translation file from Translation Tool.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	private TranslationState fileToTranslationState(File weblateFile) throws IOException, ServiceExceptionWithStatusCode {
		TranslationState translationState = new TranslationState();
		Map<Long, List<String>> stateConceptTerms = translationState.getConceptTerms();
		try (BufferedReader reader = new BufferedReader(new FileReader(weblateFile))) {
			String header = reader.readLine();
			if (!header.equals("context,target")) {
				throw new ServiceExceptionWithStatusCode("Unexpected Translation Tool file format '%s'.".formatted(header), HttpStatus.INTERNAL_SERVER_ERROR);
			}
			String line;
			while ((line = reader.readLine()) != null) {
				String[] codeTerm = line.split(",", 2);
				if (codeTerm.length == 2) {
					stateConceptTerms.computeIfAbsent(parseLong(codeTerm[0]), i -> new ArrayList<>()).add(codeTerm[1]);
				}
			}
		}
		return translationState;
	}

	@Override
	public void writeTranslation(TranslationState translationState) throws ServiceExceptionWithStatusCode {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public TranslationSourceType getType() {
		return TranslationSourceType.TRANSLATION_TOOL;
	}
}
