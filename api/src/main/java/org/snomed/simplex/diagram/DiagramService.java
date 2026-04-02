package org.snomed.simplex.diagram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class DiagramService {

	private static final Logger logger = LoggerFactory.getLogger(DiagramService.class);
	private final SnomedDiagramGeneratorClient diagramClient;
	private final DiagramResourceRepository diagramResourceRepository;

	public DiagramService(SnomedDiagramGeneratorClient diagramClient, DiagramResourceRepository diagramResourceRepository) {
		this.diagramClient = diagramClient;
		this.diagramResourceRepository = diagramResourceRepository;
	}

	/**
	 * Generates a concept diagram and persists it to configured object/file storage.
	 *
	 * @return metadata including the storage key under {@code path}
	 */
	public Map<String, Object> createAndStoreDiagram(String conceptId, SnowstormClient snowstormClient, CodeSystem codeSystem) throws ServiceException {
		File diagramFile = generateDiagramFile(conceptId, snowstormClient, codeSystem);
		try {
			String path = diagramObjectKey(conceptId);
			diagramResourceRepository.writeDiagram(path, diagramFile);
			Map<String, Object> result = new HashMap<>();
			result.put("path", path);
			result.put("conceptId", conceptId);
			logger.info("Stored diagram for concept {} at {}", conceptId, path);
			return result;
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		} catch (Exception e) {
			throw new ServiceException("Error storing diagram for concept %s".formatted(conceptId), e);
		} finally {
			cleanupDiagramFile(diagramFile);
		}
	}

	private static String diagramObjectKey(String conceptId) {
		return "diagrams/%s.png".formatted(conceptId);
	}

	private File generateDiagramFile(String conceptId, SnowstormClient snowstormClient, CodeSystem codeSystem) throws ServiceException {
		try {
			long conceptIdLong = Long.parseLong(conceptId);
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(List.of(conceptIdLong), codeSystem);
			if (concepts.isEmpty()) {
				throw new ServiceException("Concept %s not found in Snowstorm".formatted(conceptId));
			}
			return diagramClient.generateAndSaveLocalDiagram(conceptId, concepts.get(0));
		} catch (NumberFormatException e) {
			logger.error("Invalid concept ID format: {}", conceptId);
			throw new ServiceExceptionWithStatusCode("Invalid concept ID format", HttpStatus.BAD_REQUEST, e);
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
