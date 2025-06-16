package org.snomed.simplex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.snomed.simplex.weblate.domain.WeblatePage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for updating screenshots in the system.
 */
public class ScreenshotUpdater {
	
	private static final Logger logger = LoggerFactory.getLogger(ScreenshotUpdater.class);
	private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
	
	public ScreenshotUpdater() {
		// No configuration needed anymore
	}
	
	/**
	 * Updates a screenshot for a specific concept.
	 *
	 * @param conceptId The ID of the concept to update
	 * @param snowstormClient The Snowstorm client instance
	 * @param diagramClient The diagram generator client instance
	 * @param codeSystem The code system to use
	 * @return true if the screenshot was updated successfully, false otherwise
	 * @throws ServiceException if there's an error during the update process
	 */
	public boolean updateScreenshot(String conceptId, SnowstormClient snowstormClient, 
			SnomedDiagramGeneratorClient diagramClient, CodeSystem codeSystem) throws ServiceException {
		try {
			// Get concept data from Snowstorm
			Long conceptIdLong = Long.parseLong(conceptId);
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(List.of(conceptIdLong), codeSystem);
			if (concepts.isEmpty()) {
				logger.error("Concept {} not found in Snowstorm", conceptId);
				return false;
			}
			
			// Generate and save diagram
			boolean success = diagramClient.saveDiagram(conceptId, concepts.get(0));
			if (!success) {
				logger.error("Failed to generate diagram for concept {}", conceptId);
				return false;
			}
			
			logger.info("Successfully updated screenshot for concept {}", conceptId);
			return true;
			
		} catch (NumberFormatException e) {
			logger.error("Invalid concept ID format: {}", conceptId);
			throw new ServiceException("Invalid concept ID format", e);
		} catch (Exception e) {
			logger.error("Error updating screenshot for concept {}: {}", conceptId, e.getMessage());
			throw new ServiceException("Failed to update screenshot", e);
		}
	}

	private void cleanupDiagramFile(Path diagramPath) {
		try {
			Files.deleteIfExists(diagramPath);
			logger.debug("Cleaned up diagram file: {}", diagramPath);
		} catch (IOException e) {
			logger.warn("Failed to delete diagram file {}: {}", diagramPath, e.getMessage());
		}
	}

	/**
	 * Creates a screenshot in Weblate for a specific concept.
	 *
	 * @param conceptId The ID of the concept
	 * @param projectSlug The Weblate project slug
	 * @param componentSlug The Weblate component slug
	 * @param snowstormClient The Snowstorm client instance
	 * @param diagramClient The diagram generator client instance
	 * @param weblateClient The Weblate client instance
	 * @param codeSystem The code system to use
	 * @return The created screenshot data if successful, null otherwise
	 * @throws ServiceException if there's an error during the process
	 */
	public Map<String, Object> createWeblateScreenshot(String conceptId, String projectSlug, String componentSlug,
			SnowstormClient snowstormClient, SnomedDiagramGeneratorClient diagramClient, 
			WeblateClient weblateClient, CodeSystem codeSystem) throws ServiceException {
		Path diagramPath = null;
		try {
			// First generate the diagram
			boolean diagramSuccess = updateScreenshot(conceptId, snowstormClient, diagramClient, codeSystem);
			if (!diagramSuccess) {
				logger.error("Failed to generate diagram for concept {}", conceptId);
				return null;
			}

			// Get the diagram file path
			diagramPath = Paths.get(TEMP_DIR, conceptId + ".png");
			File diagramFile = diagramPath.toFile();
			if (!diagramFile.exists()) {
				logger.error("Diagram file not found at {}", diagramPath);
				return null;
			}

			// Find the Weblate unit for this concept
			WeblateUnit unit = weblateClient.getUnitForConceptId(projectSlug, componentSlug, conceptId);
			if (unit == null) {
				logger.error("No Weblate unit found for concept ID {}", conceptId);
				cleanupDiagramFile(diagramPath);
				return null;
			}

			// Create screenshot in Weblate
			Map<String, Object> screenshot = weblateClient.createScreenshot(
				projectSlug,
				componentSlug,
				unit.getId(),
				diagramFile
			);

			if (screenshot == null) {
				logger.error("Failed to create screenshot in Weblate for concept {}", conceptId);
				cleanupDiagramFile(diagramPath);
				return null;
			}

			logger.info("Successfully created screenshot in Weblate for concept {}", conceptId);
			return screenshot;

		} catch (Exception e) {
			logger.error("Error creating Weblate screenshot for concept {}: {}", conceptId, e.getMessage());
			if (diagramPath != null) {
				cleanupDiagramFile(diagramPath);
			}
			throw new ServiceException("Failed to create Weblate screenshot", e);
		} finally {
			if (diagramPath != null) {
				cleanupDiagramFile(diagramPath);
			}
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
				.collect(Collectors.toList());

			// Get all concept data in a single call
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
			Map<String, Concept> conceptMap = concepts.stream()
				.collect(Collectors.toMap(Concept::getConceptId, concept -> concept));

			// Create a map to store results
			Map<String, Map<String, Object>> results = new HashMap<>();

			// Process each unit
			for (WeblateUnit unit : units) {
				String conceptId = unit.getContext();
				Path diagramPath = null;
				try {
					Concept concept = conceptMap.get(conceptId);
					if (concept == null) {
						logger.error("Concept {} not found in Snowstorm", conceptId);
						continue;
					}

					// Generate and save diagram
					boolean diagramSuccess = diagramClient.saveDiagram(conceptId, concept);
					if (!diagramSuccess) {
						logger.error("Failed to generate diagram for concept {}", conceptId);
						continue;
					}

					// Get the diagram file path
					diagramPath = Paths.get(TEMP_DIR, conceptId + ".png");
					File diagramFile = diagramPath.toFile();
					if (!diagramFile.exists()) {
						logger.error("Diagram file not found at {}", diagramPath);
						continue;
					}

					// Create screenshot in Weblate
					Map<String, Object> screenshot = weblateClient.createScreenshot(
						projectSlug,
						componentSlug,
						unit.getId(),
						diagramFile
					);

					if (screenshot != null) {
						results.put(conceptId, screenshot);
						logger.info("Successfully created screenshot in Weblate for concept {}", conceptId);
					} else {
						logger.error("Failed to create screenshot in Weblate for concept {}", conceptId);
					}

				} catch (Exception e) {
					logger.error("Error processing concept {}: {}", conceptId, e.getMessage());
				} finally {
					if (diagramPath != null) {
						cleanupDiagramFile(diagramPath);
					}
				}
			}

			return results;

		} catch (Exception e) {
			logger.error("Error creating Weblate screenshots for batch: {}", e.getMessage());
			throw new ServiceException("Failed to create Weblate screenshots for batch", e);
		}
	}

	/**
	 * Updates screenshots for all concepts in a Weblate component.
	 *
	 * @param projectSlug The Weblate project slug
	 * @param componentSlug The Weblate component slug
	 * @param snowstormClient The Snowstorm client instance
	 * @param diagramClient The diagram generator client instance
	 * @param weblateClient The Weblate client instance
	 * @param codeSystem The code system to use
	 * @return The number of screenshots successfully created
	 * @throws ServiceException if there's an error during the process
	 */
	public int updateAll(String projectSlug, String componentSlug,
			SnowstormClient snowstormClient, SnomedDiagramGeneratorClient diagramClient,
			WeblateClient weblateClient, CodeSystem codeSystem) throws ServiceException {
		try {
			int processed = 0;
			int successful = 0;
			int startPage = 1;
			int batchSize = 1000;

			// Create screenshots directory if it doesn't exist
			Path screenshotsPath = Paths.get(TEMP_DIR);
			Files.createDirectories(screenshotsPath);

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

				if (processed % 100 == 0) {
					logger.info("Processed {}/{} units, {} successful", 
						String.format("%,d", processed), 
						String.format("%,d", totalCount), 
						String.format("%,d", successful));
				}

				startPage++;
			}

			logger.info("Completed processing {} units, {} screenshots created successfully", 
				String.format("%,d", processed), 
				String.format("%,d", successful));
			
			return successful;

		} catch (Exception e) {
			logger.error("Error updating all screenshots: {}", e.getMessage());
			throw new ServiceException("Failed to update all screenshots", e);
		}
	}
} 