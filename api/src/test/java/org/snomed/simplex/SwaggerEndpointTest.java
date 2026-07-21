package org.snomed.simplex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwaggerEndpointTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void swaggerUiIsAccessible() {
		String[] paths = {
				"/simplex/swagger-ui.html",
				"/simplex/swagger-ui/index.html"
		};
		for (String path : paths) {
			ResponseEntity<String> response = restTemplate.getForEntity(
					"http://localhost:%d%s".formatted(port, path), String.class);
			assertTrue(response.getStatusCode().is2xxSuccessful(),
					path + " returned " + response.getStatusCode());
		}
	}

	@Test
	void apiDocsAreAccessible() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"http://localhost:%d/simplex/v3/api-docs/simplex".formatted(port), String.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}

}
