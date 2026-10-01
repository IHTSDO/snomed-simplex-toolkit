package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapToSnomed;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.SimpleMapToSnomedRefsetService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple-map-to-snomed-ct")
@Tag(name = "Simple Map to SNOMED CT Refsets", description = "-")
public class SimpleMapToSnomedRefsetController extends AbstractRefsetController<RefsetMemberIntentSimpleMapToSnomed> {

	private final SimpleMapToSnomedRefsetService refsetService;

	public SimpleMapToSnomedRefsetController(
			SnowstormClientFactory snowstormClientFactory,
			ContentProcessingJobService jobService,
			ActivityService activityService,
			SimpleMapToSnomedRefsetService refsetService) {

		super(snowstormClientFactory, jobService, activityService);
		this.refsetService = refsetService;
	}

	@Override
	protected String getSpreadsheetUploadJobName() {
		return "Map upload (to SNOMED CT)";
	}

	@Override
	protected String getRefsetType() {
		return Concepts.SIMPLE_MAP_TO_SNOMEDCT_REFSET;
	}

	@Override
	protected String getFilenamePrefix() {
		return "SimpleMapToRefset";
	}

	@Override
	protected ComponentType getComponentType() {
		return ComponentType.MAP;
	}

	@Override
	protected SimpleMapToSnomedRefsetService getRefsetService() {
		return refsetService;
	}
}
