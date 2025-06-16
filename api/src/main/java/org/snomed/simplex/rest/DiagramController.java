package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.SnomedDiagramGeneratorClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.util.ScreenshotUpdater;
import org.snomed.simplex.weblate.WeblateClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@Tag(name = "Diagram", description = "Endpoints for managing SNOMED CT concept diagrams")
@RequestMapping("api")
public class DiagramController {

	private final SnowstormClientFactory snowstormClientFactory;
	private final ContentProcessingJobService jobService;
	private final ScreenshotUpdater screenshotUpdater;
	private final SnomedDiagramGeneratorClient diagramClient;
	private final WeblateClient weblateClient;

	public DiagramController(
			SnowstormClientFactory snowstormClientFactory,
			ContentProcessingJobService jobService,
			ScreenshotUpdater screenshotUpdater,
			SnomedDiagramGeneratorClient diagramClient,
			WeblateClient weblateClient) {
		super();
		this.snowstormClientFactory = snowstormClientFactory;
		this.jobService = jobService;
		this.screenshotUpdater = screenshotUpdater;
		this.diagramClient = diagramClient;
		this.weblateClient = weblateClient;
	}

	@PostMapping("{codeSystem}/diagrams/{conceptId}/update")
	@Operation(summary = "Update diagram for a single concept")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob updateSingleDiagram(
			@PathVariable String codeSystem,
			@PathVariable String conceptId) throws ServiceException, IOException {
		
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.CUSTOM_CONCEPTS, ActivityType.UPDATE);
		
		return jobService.queueContentJob(
			theCodeSystem,
			"Update diagram for concept " + conceptId,
			null,
			null,
			conceptId,
			activity,
			job -> {
				screenshotUpdater.updateScreenshot(conceptId, snowstormClient, diagramClient, theCodeSystem);
				return null;
			}
		);
	}

	@PostMapping("{codeSystem}/diagrams/update-all")
	@Operation(summary = "Update diagrams for all concepts in a Weblate component")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob updateAllDiagrams(
			@PathVariable String codeSystem,
			@RequestParam String projectSlug,
			@RequestParam String componentSlug) throws ServiceException, IOException {
		
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
				screenshotUpdater.updateAll(projectSlug, componentSlug, snowstormClient, diagramClient, weblateClient, theCodeSystem);
				return null;
			}
		);
	}
} 