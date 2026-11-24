package org.snomed.simplex.translation.service;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration.Local;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.config.TranslationCopyResourceManagerConfiguration;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationCopyPathMigrationServiceTest {

	private static final String SHORT_NAME = "SNOMEDCT-TEST";
	private static final String REFSET = "1000123";
	private static final String OLD_FILENAME = REFSET + "_TERMINOLOGY_SERVER.tsv";

	@Mock
	private SnowstormClientFactory snowstormClientFactory;
	@Mock
	private SnowstormClient snowstormClient;
	@Mock
	private AuthenticationClient authenticationClient;

	// The underlying ResourceManager's local storage only supports a path relative to the working directory
	// (an absolute path's leading '/' is stripped by ResourceConfiguration.Local), so use a target/ subdirectory
	// rather than an absolute @TempDir path.
	private Path storageDir;

	private TranslationCopyPathMigrationService migrationService;

	@BeforeEach
	void setUp() throws Exception {
		storageDir = Path.of("target", "translation-copy-migration-test-" + System.nanoTime());
		Files.createDirectories(storageDir);

		TranslationCopyResourceManagerConfiguration config = new TranslationCopyResourceManagerConfiguration();
		config.setUseCloud(false);
		config.setLocal(new Local(storageDir.toString()));

		migrationService = new TranslationCopyPathMigrationService(config, new DefaultResourceLoader(),
				snowstormClientFactory, authenticationClient, "", "", "dummy-token");
	}

	@AfterEach
	void tearDown() throws Exception {
		FileUtils.deleteDirectory(storageDir.toFile());
	}

	private CodeSystem codeSystemWithTranslationLanguages() {
		CodeSystem codeSystem = new CodeSystem("Test", SHORT_NAME, "MAIN/SNOMEDCT-TEST");
		codeSystem.setTranslationLanguages(Map.of(REFSET, "en"));
		codeSystem.setTranslationSnolateLanguages(Map.of());
		return codeSystem;
	}

	@Test
	void migratesFileFromOldFlatPathToNewCodeSystemPrefixedPath() throws Exception {
		Files.writeString(storageDir.resolve(OLD_FILENAME), "100\tterm", StandardCharsets.UTF_8);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystems(anyBoolean())).thenReturn(List.of(codeSystemWithTranslationLanguages()));

		migrationService.run(null);

		File oldFile = storageDir.resolve(OLD_FILENAME).toFile();
		File newFile = storageDir.resolve(SHORT_NAME).resolve(OLD_FILENAME).toFile();
		assertThat(oldFile).doesNotExist();
		assertThat(newFile).exists();
		assertThat(Files.readString(newFile.toPath(), StandardCharsets.UTF_8)).isEqualTo("100\tterm");
	}

	@Test
	void doesNotOverwriteAnExistingFileAtTheNewLocation() throws Exception {
		Files.writeString(storageDir.resolve(OLD_FILENAME), "old content", StandardCharsets.UTF_8);
		File newDir = storageDir.resolve(SHORT_NAME).toFile();
		newDir.mkdirs();
		Files.writeString(new File(newDir, OLD_FILENAME).toPath(), "new content", StandardCharsets.UTF_8);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystems(anyBoolean())).thenReturn(List.of(codeSystemWithTranslationLanguages()));

		migrationService.run(null);

		assertThat(storageDir.resolve(OLD_FILENAME).toFile()).exists();
		assertThat(Files.readString(storageDir.resolve(OLD_FILENAME), StandardCharsets.UTF_8)).isEqualTo("old content");
		assertThat(Files.readString(new File(newDir, OLD_FILENAME).toPath(), StandardCharsets.UTF_8)).isEqualTo("new content");
	}

	@Test
	void doesNothingWhenNoOldFileIsPresent() throws Exception {
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystems(anyBoolean())).thenReturn(List.of(codeSystemWithTranslationLanguages()));

		migrationService.run(null);

		assertThat(storageDir.resolve(SHORT_NAME)).doesNotExist();
	}

	@Test
	void skipsSnowstormCallWhenNoServiceAccountCredentialsAreConfigured() throws Exception {
		TranslationCopyResourceManagerConfiguration config = new TranslationCopyResourceManagerConfiguration();
		config.setUseCloud(false);
		config.setLocal(new Local(storageDir.toString()));
		TranslationCopyPathMigrationService service = new TranslationCopyPathMigrationService(config, new DefaultResourceLoader(),
				snowstormClientFactory, authenticationClient, "", "", "");

		service.run(null);

		verify(snowstormClientFactory, never()).getClient();
	}
}
