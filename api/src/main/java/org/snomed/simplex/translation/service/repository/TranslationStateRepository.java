package org.snomed.simplex.translation.service.repository;

import io.micrometer.common.util.StringUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.config.TranslationCopyResourceManagerConfiguration;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationSourceType;
import org.snomed.simplex.util.FileUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Repo to persist TranslationStates - they are a point in time snapshot or backup of a translation for a single language refset/dialect.
 */
@Service
public class TranslationStateRepository {

	public static final String TAB = "\t";
	public static final String LINE_BREAK = "\n";
	private final ResourceManager resourceManager;
	public static final Logger LOGGER = LoggerFactory.getLogger(TranslationStateRepository.class);

	public TranslationStateRepository(TranslationCopyResourceManagerConfiguration resourceManagerConfiguration, ResourceLoader resourceLoader) {
		resourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);
		if (!resourceManagerConfiguration.isUseCloud()) {
			String path = resourceManagerConfiguration.getLocal().getPath();
			if (!StringUtils.isEmpty(path)) {
				File localDir = new File(path);
				if (localDir.mkdirs()) {
					LOGGER.warn("Local directory {} created for translation state repository", path);
				}
			}
		}
	}

	public void saveState(String langRefsetId, TranslationSourceType source, TranslationState translationState) throws ServiceExceptionWithStatusCode {
		File tempFile = null;
		try {
			tempFile = File.createTempFile(UUID.randomUUID().toString(), ".txt");
			writeToTsv(translationState, tempFile);
			resourceManager.writeResource(getPath(langRefsetId, source), new FileInputStream(tempFile));
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to save translation state", HttpStatus.INTERNAL_SERVER_ERROR, e);
		} finally {
			FileUtils.deleteOrLogWarning(tempFile);
		}
	}

	public TranslationState loadStateOrBlank(String langRefsetId, TranslationSourceType source) throws ServiceExceptionWithStatusCode {
		try {
			String path = getPath(langRefsetId, source);
			if (resourceManager.doesObjectExist(path)) {
				try (InputStream inputStream = resourceManager.readResourceStream(path)) {
					return readFromStream(inputStream);
				}
			} else {
				return new TranslationState();
			}
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to read translation state", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	private static String getPath(String langRefsetId, TranslationSourceType source) {
		return "%s_%s.tsv".formatted(langRefsetId, source.toString());
	}

	private void writeToTsv(TranslationState translationState, File file) throws IOException {
		try (Writer writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
			for (Map.Entry<Long, List<String>> entry : translationState.getConceptTerms().entrySet()) {
				writer.write(entry.getKey().toString());
				for (String term : entry.getValue()) {
					writer.write(TAB);
					writer.write(term.replace(LINE_BREAK, "").replace(TAB, ""));
				}
				writer.write(LINE_BREAK);
			}
		}
	}

	private TranslationState readFromStream(InputStream inputStream) throws IOException {
		TranslationState translationState = new TranslationState();
		Map<Long, List<String>> map = translationState.getConceptTerms();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] split = line.split(TAB);
				if (split.length > 1) {
					Long code = Long.parseLong(split[0]);
					List<String> terms = new ArrayList<>(Arrays.asList(split));
					terms.remove(0);
					map.put(code, terms);
				}
			}
		}
		return translationState;
	}

}
