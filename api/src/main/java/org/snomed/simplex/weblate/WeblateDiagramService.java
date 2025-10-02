package org.snomed.simplex.weblate;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.exceptions.RuntimeServiceException;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class WeblateDiagramService {

	private static final Logger logger = LoggerFactory.getLogger(WeblateDiagramService.class);
	private final SnomedDiagramGeneratorClient diagramClient;
	private final WeblateClientFactory weblateClientFactory;

	private final ExecutorService screenshotExecutor;
	private final Semaphore screenshotSemaphore;

	public WeblateDiagramService(SnomedDiagramGeneratorClient diagramClient, WeblateClientFactory weblateClientFactory,
			@Value("${diagram-generator.concurrent-uploads}") int concurrentUploads) {
		// No configuration needed anymore
		this.diagramClient = diagramClient;
		this.weblateClientFactory = weblateClientFactory;
		logger.info("Initializing Weblate diagram service with {} concurrent uploads", concurrentUploads);
		screenshotExecutor = Executors.newFixedThreadPool(concurrentUploads);
		screenshotSemaphore = new Semaphore(concurrentUploads);
	}

	/**
	 * Creates a screenshot in Weblate for a specific concept.
	 *
	 * @param conceptId The ID of the concept
	 * @param projectSlug The Weblate project slug
	 * @param componentSlug The Weblate component slug
	 * @param snowstormClient The Snowstorm client instance
	 * @param codeSystem The code system to use
	 * @return The created screenshot data if successful
	 * @throws ServiceException if there's an error during the process
	 */
	public Map<String, Object> createWeblateScreenshot(String conceptId, String projectSlug, String componentSlug,
			SnowstormClient snowstormClient, CodeSystem codeSystem) throws ServiceException {

		WeblateClient weblateClient = weblateClientFactory.getClient();

		// First generate the diagram
		File diagramFile = updateScreenshot(conceptId, snowstormClient, codeSystem);

		try {
			// Find the Weblate unit for this concept
			WeblateUnit unit = weblateClient.getUnitForConceptId(projectSlug, componentSlug, conceptId, "en");
			if (unit == null) {
				logger.error("No Weblate unit found for concept ID {}", conceptId);
				return Collections.emptyMap();
			}

			// Create screenshot in Weblate
			Map<String, Object> screenshot = weblateClient.uploadAndAssociateScreenshot(conceptId, projectSlug, componentSlug,
					unit.getId(), diagramFile);

			if (screenshot == null) {
				logger.error("Failed to create screenshot in Weblate for concept {}", conceptId);
			}

			logger.info("Successfully created screenshot in Weblate for concept {}", conceptId);
			return screenshot;
		} catch (Exception e) {
			throw new ServiceException("Error creating Weblate screenshot for concept %s".formatted(conceptId), e);
		} finally {
			cleanupDiagramFile(diagramFile);
		}
	}

	/**
	 * Updates a screenshot for a specific concept.
	 *
	 * @param conceptId       The ID of the concept to update
	 * @param snowstormClient The Snowstorm client instance
	 * @param codeSystem      The code system to use
	 * @return local file with screenshot
	 * @throws ServiceException if there's an error during the update process
	 */
	private File updateScreenshot(String conceptId, SnowstormClient snowstormClient, CodeSystem codeSystem) throws ServiceException {
		try {
			// Get concept data from Snowstorm
			Long conceptIdLong = Long.parseLong(conceptId);
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(List.of(conceptIdLong), codeSystem);
			if (concepts.isEmpty()) {
				throw new ServiceException("Concept %s not found in Snowstorm".formatted(conceptId));
			}

			// Generate and save diagram
			return diagramClient.generateAndSaveLocalDiagram(conceptId, concepts.get(0));
		} catch (NumberFormatException e) {
			logger.error("Invalid concept ID format: {}", conceptId);
			throw new ServiceExceptionWithStatusCode("Invalid concept ID format", HttpStatus.BAD_REQUEST, e);
		}
	}

	public void createWeblateScreenshot(WeblateUnit unit, Concept concept, String project, String component, WeblateClient weblateClient) {
		try {
			// Try to acquire a permit immediately - if none available, this will block
			screenshotSemaphore.acquire();

			// Submit the task to the thread pool
			screenshotExecutor.submit(() -> {
				try {
					processUnit(project, component, diagramClient, weblateClient, unit, concept, new HashMap<>());
					return null;
				} finally {
					// Always release the semaphore permit when done
					screenshotSemaphore.release();
				}
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeServiceException("Thread was interrupted while waiting for available thread", e);
		}
	}

	private void processUnit(String projectSlug, String componentSlug, SnomedDiagramGeneratorClient diagramClient,
			WeblateClient weblateClient, WeblateUnit unit, Concept concept, Map<String, Map<String, Object>> results) {

		File diagramFile = null;
		String conceptId = concept.getConceptId();
		try {

			// Generate and save diagram
			diagramFile = diagramClient.generateAndSaveLocalDiagram(conceptId, concept);

			// Create screenshot in Weblate
			Map<String, Object> screenshot = weblateClient.uploadAndAssociateScreenshot(conceptId, projectSlug, componentSlug,
				unit.getId(), diagramFile);

			if (screenshot != null) {
				results.put(conceptId, screenshot);
				logger.info("Successfully created screenshot in Weblate for concept {}", conceptId);
			} else {
				logger.error("Failed to create screenshot in Weblate for concept {}", conceptId);
			}

		} catch (Exception e) {
			logger.error("Error processing concept {}: {}", conceptId, e.getMessage());
		} finally {
			cleanupDiagramFile(diagramFile);
		}
	}

	private void cleanupDiagramFile(File diagramFile) {
		if (diagramFile != null) {
			Path diagramPath = diagramFile.toPath();
			try {
				Files.deleteIfExists(diagramPath);
				logger.debug("Cleaned up diagram file: {}", diagramPath);
			} catch (IOException e) {
				logger.warn("Failed to delete diagram file {}: {}", diagramPath, e.getMessage());
			}
		}
	}

	@PreDestroy
	public void shutdown() {
		logger.info("Shutting down WeblateDiagramService executor service");
		screenshotExecutor.shutdown();
		try {
			if (!screenshotExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
				logger.warn("Executor did not terminate gracefully, forcing shutdown");
				screenshotExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			logger.warn("Interrupted while waiting for executor termination");
			screenshotExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
