package org.snomed.simplex.standardcodelist;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.config.StandardCodeListResourceManagerConfiguration;
import org.springframework.core.io.FileSystemResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StandardCodeListRepositoryTest {

	private static final String CATALOGUE_ID = "TEST_CATALOGUE";

	/** Must be relative: ResourceConfiguration.Local strips a leading '/', so an absolute path would resolve incorrectly. */
	private static final String STORAGE_RELATIVE_PATH = "target/test-standard-code-lists";

	private StandardCodeListRepository repository;

	@BeforeEach
	void setUp() throws IOException {
		Path storageRoot = Paths.get(STORAGE_RELATIVE_PATH);
		Path catalogueDir = storageRoot.resolve(CATALOGUE_ID);
		Files.createDirectories(catalogueDir);
		Files.writeString(catalogueDir.resolve("metadata.json"), """
				{
				  "id": "TEST_CATALOGUE",
				  "name": "Test Catalogue",
				  "codeSystem": "SNOMED CT",
				  "valueSets": [
				    {
				      "name": "exampleValueSet",
				      "valueSetId": "1.2.3",
				      "description": "An example value set.",
				      "codeSystem": "SNOMED CT",
				      "codeSystemVersion": "2026-01-01",
				      "conceptCount": 2,
				      "file": "exampleValueSet.tsv",
				      "partialExtract": false
				    }
				  ]
				}
				""");
		Files.writeString(catalogueDir.resolve("exampleValueSet.tsv"), "code\tterm\n123456\tExample concept\n789012\tAnother concept\n");

		StandardCodeListResourceManagerConfiguration configuration = new StandardCodeListResourceManagerConfiguration();
		configuration.setReadonly(true);
		configuration.setUseCloud(false);
		ResourceConfiguration.Local local = new ResourceConfiguration.Local();
		local.setPath(STORAGE_RELATIVE_PATH);
		configuration.setLocal(local);
		repository = new StandardCodeListRepository(configuration, new FileSystemResourceLoader(), new ObjectMapper());
	}

	@AfterEach
	void tearDown() throws IOException {
		Path storageRoot = Paths.get(STORAGE_RELATIVE_PATH);
		if (Files.exists(storageRoot)) {
			try (var stream = Files.walk(storageRoot)) {
				stream.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException ignored) {
						// best-effort cleanup
					}
				});
			}
		}
	}

	@Test
	void listCatalogueIdsFindsTestCatalogue() {
		Set<String> catalogueIds = repository.listCatalogueIds();
		assertTrue(catalogueIds.contains(CATALOGUE_ID), "Expected test fixture catalogue to be present: " + catalogueIds);
	}

	@Test
	void readMetadataReturnsValueSets() {
		StandardCodeListMetadata metadata = repository.readMetadataOrNull(CATALOGUE_ID);
		assertNotNull(metadata);
		assertEquals(CATALOGUE_ID, metadata.getId());
		assertEquals(1, metadata.getValueSets().size());
		assertEquals("exampleValueSet", metadata.getValueSets().get(0).getName());
	}

	@Test
	void readMetadataReturnsNullForUnknownCatalogue() {
		assertNull(repository.readMetadataOrNull("DOES_NOT_EXIST"));
	}

	@Test
	void readConceptCodesReadsTsvFile() throws Exception {
		StandardCodeListMetadata metadata = repository.readMetadataOrNull(CATALOGUE_ID);
		StandardCodeListValueSetMetadata valueSet = metadata.getValueSets().get(0);

		List<String> codes = repository.readConceptCodes(CATALOGUE_ID, valueSet.getFile());
		assertEquals(List.of("123456", "789012"), codes);
	}
}
