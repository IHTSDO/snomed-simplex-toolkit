package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.RefsetMemberIntent;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.RefsetToolSubsetReader;
import org.snomed.simplex.service.SimpleRefsetService;
import org.snomed.simplex.service.job.AsyncJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple")
@Tag(name = "Simple Refsets", description = "-")
public class SimpleRefsetController extends AbstractRefsetController<RefsetMemberIntent> {

	private final SimpleRefsetService simpleRefsetService;
	private final ContentProcessingJobService jobService;

	public SimpleRefsetController(SnowstormClientFactory snowstormClientFactory,
			SimpleRefsetService simpleRefsetService, ContentProcessingJobService jobService, ActivityService activityService) {

		super(snowstormClientFactory, jobService, activityService);
		this.simpleRefsetService = simpleRefsetService;
		this.jobService = jobService;
	}

	@Override
	protected String getRefsetType() {
		return Concepts.SIMPLE_TYPE_REFSET;
	}

	@Override
	protected String getFilenamePrefix() {
		return "SimpleRefset";
	}

	@Override
	protected ComponentType getComponentType() {
		return ComponentType.SUBSET;
	}

	@Override
	protected SimpleRefsetService getRefsetService() {
		return simpleRefsetService;
	}

	@PutMapping(path = "{refsetId}/refset-tool", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadRefsetToolSubset(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			UriComponentsBuilder uriComponentBuilder) throws ServiceException, IOException {

		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.SUBSET, ActivityType.UPDATE);
		return jobService.queueContentJob(theCodeSystem, "Subset upload (Refset Tool)", file.getInputStream(), file.getOriginalFilename(), refsetId,
				activity, asyncJob -> getRefsetService().updateRefsetViaCustomFile(asyncJob, new RefsetToolSubsetReader(asyncJob.getInputStream())));
	}

	@Override
	protected String getSpreadsheetUploadJobName() {
		return "Subset upload (Spreadsheet)";
	}

}
