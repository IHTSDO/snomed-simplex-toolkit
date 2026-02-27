package org.snomed.simplex.translation.service;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormExportConfiguration;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.weblate.domain.Term;
import org.snomed.simplex.weblate.rf2.LightweightTermComponentFactory;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static java.lang.Long.parseLong;

public class SnowstormTranslationSource implements TranslationSource {

	private final SnowstormClient snowstormClient;
	private final CodeSystem codeSystem;
	private final String language;
	private final Long languageRefsetId;

	public SnowstormTranslationSource(SnowstormClient snowstormClient, CodeSystem codeSystem, String language, String languageRefsetId) {
		this.snowstormClient = snowstormClient;
		this.codeSystem = codeSystem;
		this.language = language;
		this.languageRefsetId = parseLong(languageRefsetId);
	}

	@Override
	public TranslationState readTranslation() throws ServiceExceptionWithStatusCode {
		try {
			File tempFile = Files.createTempFile(codeSystem.getShortName() + UUID.randomUUID(), ".zip").toFile();
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				SnowstormExportConfiguration exportConfiguration = new SnowstormExportConfiguration(SnowstormClient.ExportType.SNAPSHOT, codeSystem)
					.setLanguageOnly(true)
					.addModuleId(codeSystem.getDefaultModule());
				snowstormClient.exportRF2(fos, exportConfiguration);
			}

			LightweightTermComponentFactory lightweightTermComponentFactory = new LightweightTermComponentFactory(language, languageRefsetId);
			LoadingProfile loadingProfile = LoadingProfile.light.withRefset(languageRefsetId.toString());
			new ReleaseImporter().loadSnapshotReleaseFiles(new FileInputStream(tempFile), loadingProfile, lightweightTermComponentFactory, false);
			return getTranslationState(lightweightTermComponentFactory.getConceptTerms());
		} catch (IOException | ServiceException | ReleaseImportException e) {
			throw new ServiceExceptionWithStatusCode("Failed to load translation from terminology server.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	TranslationState getTranslationState(Map<Long, Map<Long, Term>> exportedConceptTerms) {
		TranslationState translationState = new TranslationState();
		Map<Long, List<String>> stateConceptTerms = translationState.getConceptTerms();

		Comparator<Term> comparingPreferredThenAlphabetical = Comparator.comparing((Function<Term, Boolean>) term ->
			term.getAcceptabilityMap().get(languageRefsetId).equals(Long.parseLong(Concepts.PREFERRED)), Comparator.reverseOrder())
			.thenComparing(Term::getTermString);

		for (Map.Entry<Long, Map<Long, Term>> entry : exportedConceptTerms.entrySet()) {
			List<String> conceptTerms = entry.getValue().values().stream()
				.filter(term -> term.getAcceptabilityMap().containsKey(languageRefsetId))
				.sorted(comparingPreferredThenAlphabetical)
				.map(Term::getTermString)
				.toList();
			stateConceptTerms.put(entry.getKey(), conceptTerms);
		}

		return translationState;
	}

	@Override
	public void writeTranslation(TranslationState translationState) {
		throw new UnsupportedOperationException("Not implemented yet.");
	}

	@Override
	public TranslationSourceType getType() {
		return TranslationSourceType.TERMINOLOGY_SERVER;
	}
}
