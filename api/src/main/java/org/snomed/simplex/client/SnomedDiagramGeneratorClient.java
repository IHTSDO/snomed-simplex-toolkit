package org.snomed.simplex.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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
	 * @param conceptId   The concept ID
	 * @param conceptData The concept data from the SNOMED CT API
	 * @return local file of the diagram
	 */
	public File generateAndSaveLocalDiagram(String conceptId, Concept conceptData) throws ServiceException {
		try {
			// Make POST request to diagram generation server
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Concept> requestEntity = new HttpEntity<>(conceptData, headers);
			ResponseEntity<Resource> response = restTemplate.exchange(
					DIAGRAM_ENDPOINT,
					HttpMethod.POST,
					requestEntity,
					Resource.class
			);

			Resource body = response.getBody();
			File outputFile = new File(TEMP_DIR, conceptId + ".png");
			if (response.getStatusCode().is2xxSuccessful() && body != null) {
				// Save the response as PNG file in temp directory
				try (FileOutputStream fos = new FileOutputStream(outputFile)) {
					body.getInputStream().transferTo(fos);
				}
				logger.debug("Successfully saved diagram for concept {} to {}", conceptId, outputFile.getAbsolutePath());
			} else {
				throw new ServiceException("Failed to save local diagram for concept %s".formatted(conceptId));
			}
			return outputFile;
		} catch (RestClientResponseException e) {
			throw new ServiceException("Failed to fetch diagram for concept %s".formatted(conceptId), e);
		} catch (IOException e) {
			throw new ServiceException("Failed to save local copy of diagram for concept %s".formatted(conceptId));
		}
	}
} 