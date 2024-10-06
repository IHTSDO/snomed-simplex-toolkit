package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.RefsetMemberIntentSimpleMapToSnomedWithCorrelation;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.SimpleMapToSnomedWithCorrelationRefsetService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple-map-to-snomed-with-correlation")
@Tag(name = "Simple Map to SNOMED with Correlation Refsets", description = "-")
public class SimpleMapToSnomedWithCorrelationRefsetController extends AbstractRefsetController<RefsetMemberIntentSimpleMapToSnomedWithCorrelation> {

	private final SimpleMapToSnomedWithCorrelationRefsetService refsetService;

	public SimpleMapToSnomedWithCorrelationRefsetController(
			SnowstormClientFactory snowstormClientFactory,
			ContentProcessingJobService jobService,
			ActivityService activityService,
			SimpleMapToSnomedWithCorrelationRefsetService refsetService) {

		super(snowstormClientFactory, jobService, activityService);
		this.refsetService = refsetService;
	}

	@Override
	protected String getSpreadsheetUploadJobName() {
		return "Map upload";
	}

	@Override
	protected String getRefsetType() {
		return Concepts.SIMPLE_MAP_WITH_CORRELATION_TO_SNOMEDCT_REFSET;
	}

	@Override
	protected String getFilenamePrefix() {
		return "MapRefset";
	}

	@Override
	protected ComponentType getComponentType() {
		return ComponentType.MAP;
	}

	@Override
	protected SimpleMapToSnomedWithCorrelationRefsetService getRefsetService() {
		return refsetService;
	}
}
