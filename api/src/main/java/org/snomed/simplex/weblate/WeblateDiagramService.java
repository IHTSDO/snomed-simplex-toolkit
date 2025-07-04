package org.snomed.simplex.weblate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class WeblateDiagramService {
	
	private static final Logger logger = LoggerFactory.getLogger(WeblateDiagramService.class);
	private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
	private final SnomedDiagramGeneratorClient diagramClient;
	private final WeblateClientFactory weblateClientFactory;
	private final int concurrentUploads;

	public WeblateDiagramService(SnomedDiagramGeneratorClient diagramClient, WeblateClientFactory weblateClientFactory,
			@Value("${diagram-generator.concurrent-uploads}") int concurrentUploads) {
		// No configuration needed anymore
		this.diagramClient = diagramClient;
		this.weblateClientFactory = weblateClientFactory;
		this.concurrentUploads = concurrentUploads;
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
			WeblateUnit unit = weblateClient.getUnitForConceptId(projectSlug, componentSlug, conceptId);
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

	/**
	 * Creates screenshots in Weblate for a batch of units efficiently.
	 *
	 * @param units The list of Weblate units to process
	 * @param projectSlug The Weblate project slug
	 * @param componentSlug The Weblate component slug
	 * @param snowstormClient The Snowstorm client instance
	 * @param diagramClient The diagram generator client instance
	 * @param weblateClient The Weblate client instance
	 * @param codeSystem The code system to use
	 * @return A map of concept IDs to their created screenshot data
	 * @throws ServiceException if there's an error during the process
	 */
	public Map<String, Map<String, Object>> createWeblateScreenshotsForBatch(List<WeblateUnit> units, 
			String projectSlug, String componentSlug, SnowstormClient snowstormClient, 
			SnomedDiagramGeneratorClient diagramClient, WeblateClient weblateClient, 
			CodeSystem codeSystem) throws ServiceException {
		try {
			// Extract concept IDs from units
			List<Long> conceptIds = units.stream()
				.map(WeblateUnit::getContext)
				.map(Long::parseLong)
				.toList();

			// Get all concept data in a single call
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
			Map<String, Concept> conceptMap = concepts.stream()
				.collect(Collectors.toMap(Concept::getConceptId, concept -> concept));

			// Create a map to store results
			Map<String, Map<String, Object>> results = new ConcurrentHashMap<>();

			ExecutorService executor = Executors.newFixedThreadPool(concurrentUploads);
			List<Future<Void>> futures = new ArrayList<>();

			// Process each unit
			for (WeblateUnit unit : units) {
				String conceptId = unit.getContext();
				Concept concept = conceptMap.get(conceptId);
				if (concept == null) {
					logger.error("Concept {} not found in Snowstorm", conceptId);
					continue;
				}
				futures.add(executor.submit(() -> {
					processUnit(projectSlug, componentSlug, diagramClient, weblateClient, unit, concept, results);
					return null;
				}));
			}

			// Wait for all tasks to complete
			waitForAllFutures(futures);
			return results;
		} catch (Exception e) {
			if (e.getClass().isAssignableFrom(InterruptedException.class)) {
				Thread.currentThread().interrupt();
			}
			throw new ServiceException("Error creating Weblate screenshots for batch", e);
		}
	}

	private static void waitForAllFutures(List<Future<Void>> futures) throws InterruptedException {
		try {
			for (Future<Void> future : futures) {
				future.get();
			}
		} catch (ExecutionException e) {
			logger.error("Error processing unit: {}", e.getMessage());
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

	/**
	 * Updates screenshots for all concepts in a Weblate component.
	 *
	 * @param projectSlug The Weblate project slug
	 * @param componentSlug The Weblate component slug
	 * @param snowstormClient The Snowstorm client instance
	 * @param codeSystem The code system to use
	 * @throws ServiceException if there's an error during the process
	 */
	public void updateAll(String projectSlug, String componentSlug, SnowstormClient snowstormClient,
			CodeSystem codeSystem) throws ServiceException {

		try {
			int processed = 0;
			int successful = 0;
			int startPage = 1;
			int batchSize = 1000;

			// Create screenshots directory if it doesn't exist
			Path screenshotsPath = Paths.get(TEMP_DIR);
			Files.createDirectories(screenshotsPath);

			WeblateClient weblateClient = weblateClientFactory.getClient();

			// Get initial page to get total count
			WeblatePage<WeblateUnit> initialPage = weblateClient.getUnitPage(projectSlug, componentSlug);
			int totalCount = initialPage.count();

			while (true) {
				// Get a batch of units from Weblate
				List<WeblateUnit> batch = weblateClient.getUnitStream(projectSlug, componentSlug, startPage)
						.getBatch(batchSize);
				
				if (batch.isEmpty()) {
					break;
				}

				// Process the batch efficiently
				Map<String, Map<String, Object>> batchResults = createWeblateScreenshotsForBatch(
					batch, projectSlug, componentSlug,
					snowstormClient, diagramClient, weblateClient, codeSystem
				);

				processed += batch.size();
				successful += batchResults.size();

				if (processed % 100 == 0 && logger.isInfoEnabled()) {
					logger.info("Processed {}/{} units, {} successful",
							String.format("%,d", processed),
							String.format("%,d", totalCount),
							String.format("%,d", successful));
				}

				startPage++;
			}

			if (logger.isInfoEnabled()) {
				logger.info("Completed processing {} units, {} screenshots created successfully",
						String.format("%,d", processed),
						String.format("%,d", successful));
			}
		} catch (Exception e) {
			throw new ServiceException("Failed to update all screenshots", e);
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

} 