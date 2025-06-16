package org.snomed.simplex.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class SnomedDiagramGeneratorClient {

	private static final Logger logger = LoggerFactory.getLogger(SnomedDiagramGeneratorClient.class);
	private static final String DIAGRAM_ENDPOINT = "/diagram";
	private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

	private final RestTemplate restTemplate;

	public SnomedDiagramGeneratorClient(@Value("${diagram-generator.url}") String serverUrl) {
		this.restTemplate = new RestTemplateBuilder()
				.rootUri(serverUrl)
				.build();
	}

	/**
	 * Save a diagram for a concept to a file.
	 *
	 * @param conceptId The concept ID
	 * @param conceptData The concept data from the SNOMED CT API
	 * @return true if the diagram was saved successfully, false otherwise
	 */
	public boolean saveDiagram(String conceptId, Object conceptData) {
		try {
			// Make POST request to diagram generation server
			ResponseEntity<Resource> response = restTemplate.exchange(
					DIAGRAM_ENDPOINT,
					HttpMethod.POST,
					new HttpEntity<>(conceptData),
					Resource.class
			);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				// Save the response as PNG file in temp directory
				File outputFile = new File(TEMP_DIR, conceptId + ".png");
				try (FileOutputStream fos = new FileOutputStream(outputFile)) {
					response.getBody().getInputStream().transferTo(fos);
				}
				logger.info("Successfully saved diagram for concept {} to {}", conceptId, outputFile.getAbsolutePath());
				return true;
			} else {
				logger.error("Failed to generate diagram for concept {}: Invalid response from server", conceptId);
				return false;
			}

		} catch (HttpStatusCodeException e) {
			logger.error("HTTP error while saving diagram for concept {}: {}", conceptId, e.getMessage());
			return false;
		} catch (IOException e) {
			logger.error("IO error while saving diagram for concept {}: {}", conceptId, e.getMessage());
			return false;
		} catch (Exception e) {
			logger.error("Unexpected error while saving diagram for concept {}: {}", conceptId, e.getMessage());
			return false;
		}
	}
} 