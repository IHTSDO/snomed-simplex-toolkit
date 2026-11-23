package org.snomed.simplex.standardcodelist;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.config.StandardCodeListResourceManagerConfiguration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Reads standard code list catalogues (metadata.json + per-value-set TSV files) from local disk or S3. */
@Service
public class StandardCodeListRepository {

	private static final String METADATA_FILENAME = "metadata.json";

	private static final Logger LOGGER = LoggerFactory.getLogger(StandardCodeListRepository.class);

	private final ResourceManager resourceManager;
	private final ObjectMapper objectMapper;

	public StandardCodeListRepository(StandardCodeListResourceManagerConfiguration resourceManagerConfiguration,
			ResourceLoader resourceLoader, ObjectMapper objectMapper) {

		this.resourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);
		this.objectMapper = objectMapper;
	}

	/** Catalogue ids are the top-level folder names under the storage root, each containing a metadata.json. */
	public Set<String> listCatalogueIds() {
		Set<String> ids = new TreeSet<>();
		try {
			for (String filename : resourceManager.listFilenames()) {
				int slash = filename.indexOf('/');
				ids.add(slash == -1 ? filename : filename.substring(0, slash));
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to list standard code list catalogues.", e);
		}
		return ids;
	}

	public StandardCodeListMetadata readMetadataOrNull(String catalogueId) {
		try (InputStream inputStream = resourceManager.readResourceStream(catalogueId + "/" + METADATA_FILENAME)) {
			return objectMapper.readValue(inputStream, StandardCodeListMetadata.class);
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException e) {
			LOGGER.warn("Failed to read standard code list metadata for catalogue '{}'.", catalogueId, e);
			return null;
		}
	}

	/** Reads the first (tab-separated) column of each data row, skipping the header row. */
	public List<String> readConceptCodes(String catalogueId, String tsvFilename) throws IOException {
		List<String> codes = new ArrayList<>();
		try (InputStream inputStream = resourceManager.readResourceStream(catalogueId + "/" + tsvFilename);
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			if (header == null) {
				return codes;
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				int tab = line.indexOf('\t');
				String code = (tab == -1 ? line : line.substring(0, tab)).trim();
				if (!code.isEmpty()) {
					codes.add(code);
				}
			}
		}
		return codes;
	}
}
