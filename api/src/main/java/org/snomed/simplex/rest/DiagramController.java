package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateDiagramService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Diagram", description = "Endpoints for managing SNOMED CT concept diagrams")
@RequestMapping("api")
public class DiagramController {

	private final WeblateDiagramService weblateDiagramService;
    private final SnowstormClientFactory snowstormClientFactory;

	public DiagramController(WeblateDiagramService weblateDiagramService, SnowstormClientFactory snowstormClientFactory) {
		this.weblateDiagramService = weblateDiagramService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@PostMapping("diagrams/{conceptId}/update")
	@Operation(summary = "Update diagram for a single concept")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public Map<String, Object> updateSingleDiagram(@PathVariable String conceptId) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);
		return weblateDiagramService.createWeblateScreenshot(conceptId, WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT,
				snowstormClient, theCodeSystem);
	}

}
