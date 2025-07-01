package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateDiagramService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@Tag(name = "Diagram", description = "Endpoints for managing SNOMED CT concept diagrams")
@RequestMapping("api")
public class DiagramController {

	private final WeblateDiagramService weblateDiagramService;
    private final SnowstormClientFactory snowstormClientFactory;
    private final ContentProcessingJobService jobService;

	public DiagramController(WeblateDiagramService weblateDiagramService, SnowstormClientFactory snowstormClientFactory, ContentProcessingJobService jobService) {
		this.weblateDiagramService = weblateDiagramService;
		this.snowstormClientFactory = snowstormClientFactory;
		this.jobService = jobService;
	}

	@PostMapping("{codeSystem}/diagrams/{conceptId}/update")
	@Operation(summary = "Update diagram for a single concept")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Map<String, Object> updateSingleDiagram(
			@PathVariable String codeSystem,
			@PathVariable String conceptId) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return weblateDiagramService.createWeblateScreenshot(conceptId, WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT,
				snowstormClient, theCodeSystem);
	}

	@PostMapping("{codeSystem}/diagrams/update-all")
	@Operation(summary = "Update diagrams for all concepts in a Weblate component")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob updateAllDiagrams(
			@PathVariable String codeSystem,
			@RequestParam String projectSlug,
			@RequestParam String componentSlug,
			@RequestParam(required = false) String lastCompletedConcept) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.CUSTOM_CONCEPTS, ActivityType.UPDATE);

		return jobService.queueContentJob(
			theCodeSystem,
			"Update all diagrams for project " + projectSlug + " component " + componentSlug,
			null,
			null,
			null,
			activity,
			job -> {
                weblateDiagramService.updateAll(projectSlug, componentSlug, snowstormClient, theCodeSystem, lastCompletedConcept);
				return null;
			}
		);
	}
}
