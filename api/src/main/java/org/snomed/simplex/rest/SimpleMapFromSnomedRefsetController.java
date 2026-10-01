package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapFromSnomed;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.SimpleMapFromSnomedRefsetService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple-map-from-snomed-ct")
@Tag(name = "Simple Map from SNOMED CT Refsets", description = "-")
public class SimpleMapFromSnomedRefsetController extends AbstractRefsetController<RefsetMemberIntentSimpleMapFromSnomed> {

	private final SimpleMapFromSnomedRefsetService refsetService;

	public SimpleMapFromSnomedRefsetController(
			SnowstormClientFactory snowstormClientFactory,
			ContentProcessingJobService jobService,
			ActivityService activityService,
			SimpleMapFromSnomedRefsetService refsetService) {

		super(snowstormClientFactory, jobService, activityService);
		this.refsetService = refsetService;
	}

	@Override
	protected String getSpreadsheetUploadJobName() {
		return "Map upload (from SNOMED CT)";
	}

	@Override
	protected String getRefsetType() {
		return Concepts.SIMPLE_MAP_FROM_SNOMEDCT_REFSET;
	}

	@Override
	protected String getFilenamePrefix() {
		return "SimpleMapFromRefset";
	}

	@Override
	protected ComponentType getComponentType() {
		return ComponentType.MAP;
	}

	@Override
	protected SimpleMapFromSnomedRefsetService getRefsetService() {
		return refsetService;
	}
}
