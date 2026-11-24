package org.snomed.simplex.translation.service;

import io.micrometer.common.util.StringUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.config.TranslationCopyResourceManagerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-off startup migration: translation-copy snapshots used to be stored as a flat
 * {@code <langRefsetId>_<source>.tsv} file; they are now stored under a code system shortname prefix
 * ({@code <codeSystemShortName>/<langRefsetId>_<source>.tsv}, see {@link org.snomed.simplex.translation.service.repository.TranslationStateRepository}).
 * <p>
 * On every startup this walks every code system, finds the language refsets that have been added to the
 * Translation Studio (standard or Snolate), and moves any file still at the old flat path to its new location.
 * The move is skipped, with a warning, if a file already exists at the new location. Already-migrated or
 * never-existing files are silently left alone, so this is safe to run on every boot.
 */
@Service
@Profile("!test")
public class TranslationCopyPathMigrationService implements ApplicationRunner {

	private static final String JOB_USERNAME = "Translation Copy Path Migration";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ResourceManager resourceManager;
	private final TranslationCopyResourceManagerConfiguration resourceManagerConfiguration;
	private final SnowstormClientFactory snowstormClientFactory;
	private final AuthenticationClient authenticationClient;
	private final String serviceAccountUsername;
	private final String serviceAccountPassword;
	private final String devWorkaroundImsToken;

	public TranslationCopyPathMigrationService(
			TranslationCopyResourceManagerConfiguration resourceManagerConfiguration,
			ResourceLoader resourceLoader,
			SnowstormClientFactory snowstormClientFactory,
			AuthenticationClient authenticationClient,
			@Value("${managed-service.service-account.username:}") String serviceAccountUsername,
			@Value("${managed-service.service-account.password:}") String serviceAccountPassword,
			@Value("${managed-service.service-account.dev-workaround-ims-token:}") String devWorkaroundImsToken) {

		this.resourceManagerConfiguration = resourceManagerConfiguration;
		this.resourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);
		this.snowstormClientFactory = snowstormClientFactory;
		this.authenticationClient = authenticationClient;
		this.serviceAccountUsername = serviceAccountUsername;
		this.serviceAccountPassword = serviceAccountPassword;
		this.devWorkaroundImsToken = devWorkaroundImsToken;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!hasCredentials() && !StringUtils.isNotEmpty(devWorkaroundImsToken)) {
			logger.info("Skipping translation-copy storage path migration: neither managed-service.service-account.username/password "
					+ "nor managed-service.service-account.dev-workaround-ims-token is configured, so code systems cannot be listed from Snowstorm.");
			return;
		}
		try {
			authenticateAsServiceAccount();
			migrate();
		} catch (Exception e) {
			// A failed migration must never prevent the application from starting.
			logger.error("Translation-copy storage path migration failed: {}", e.getMessage(), e);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void migrate() throws Exception {
		List<CodeSystem> codeSystems = snowstormClientFactory.getClient().getCodeSystems(true);
		int migrated = 0;
		int skipped = 0;
		for (CodeSystem codeSystem : codeSystems) {
			String shortName = codeSystem.getShortName();
			Set<String> langRefsetIds = new LinkedHashSet<>();
			if (codeSystem.getTranslationLanguages() != null) {
				langRefsetIds.addAll(codeSystem.getTranslationLanguages().keySet());
			}
			if (codeSystem.getTranslationSnolateLanguages() != null) {
				langRefsetIds.addAll(codeSystem.getTranslationSnolateLanguages().keySet());
			}
			for (String langRefsetId : langRefsetIds) {
				for (TranslationSourceType source : TranslationSourceType.values()) {
					if (migrateFile(shortName, langRefsetId, source)) {
						migrated++;
					} else {
						skipped++;
					}
				}
			}
		}
		logger.info("Translation-copy storage path migration complete: moved {} file(s), skipped {} (already migrated or not present).", migrated, skipped);
	}

	private boolean migrateFile(String codeSystemShortName, String langRefsetId, TranslationSourceType source) {
		String oldPath = "%s_%s.tsv".formatted(langRefsetId, source);
		String newPath = "%s/%s_%s.tsv".formatted(codeSystemShortName, langRefsetId, source);
		try {
			if (!resourceManager.doesObjectExist(oldPath)) {
				return false;
			}
			if (doesResourceExist(newPath)) {
				logger.warn("Not migrating translation-copy snapshot {}: destination {} already exists.", oldPath, newPath);
				return false;
			}
			ensureLocalDirectory(codeSystemShortName);
			resourceManager.moveResource(oldPath, newPath);
			logger.info("Migrated translation-copy snapshot {} -> {}", oldPath, newPath);
			return true;
		} catch (IOException e) {
			logger.error("Failed to migrate translation-copy snapshot {} -> {}: {}", oldPath, newPath, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * {@link ResourceManager#doesObjectExist(String)} lists only the top-level filenames of the local storage
	 * directory, so it never matches a nested resourcePath (e.g. {@code <codeSystemShortName>/<file>}) when not
	 * using cloud storage. Check the local filesystem directly in that case.
	 */
	private boolean doesResourceExist(String resourcePath) {
		if (resourceManagerConfiguration.isUseCloud()) {
			return resourceManager.doesObjectExist(resourcePath);
		}
		String localPath = resourceManagerConfiguration.getLocal().getPath();
		return !StringUtils.isEmpty(localPath) && new File(localPath, resourcePath).isFile();
	}

	private void ensureLocalDirectory(String codeSystemShortName) {
		if (resourceManagerConfiguration.isUseCloud()) {
			return;
		}
		String localPath = resourceManagerConfiguration.getLocal().getPath();
		if (!StringUtils.isEmpty(localPath)) {
			File dir = new File(localPath, codeSystemShortName);
			if (dir.mkdirs()) {
				logger.debug("Created local directory {} for translation-copy storage migration", dir);
			}
		}
	}

	private boolean hasCredentials() {
		return StringUtils.isNotEmpty(serviceAccountUsername) && StringUtils.isNotEmpty(serviceAccountPassword);
	}

	private void authenticateAsServiceAccount() throws Exception {
		String principal = serviceAccountUsername;
		String token;
		if (hasCredentials()) {
			token = authenticationClient.fetchAuthenticationToken(serviceAccountUsername, serviceAccountPassword);
		} else {
			// Temporary workaround: no username/password configured, use the fixed IMS token.
			logger.warn("Using managed-service.service-account.dev-workaround-ims-token to authenticate; this is a temporary workaround");
			token = devWorkaroundImsToken;
			principal = StringUtils.isNotEmpty(principal) ? principal : JOB_USERNAME;
		}
		SecurityContext context = new SecurityContextImpl(
				new PreAuthenticatedAuthenticationToken(principal, token, List.of()));
		SecurityContextHolder.setContext(context);
	}
}
