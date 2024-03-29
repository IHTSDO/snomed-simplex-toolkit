package org.snomed.simplex.rest;

import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.service.RefsetUpdateService;
import org.snomed.simplex.service.SimpleMapToSnomedWithCorrelationRefsetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple-map-to-snomed-with-correlation")
@Tag(name = "Simple Map to SNOMED with Correlation Refsets", description = "-")
public class SimpleMapToSnomedWithCorrelationRefsetController extends AbstractRefsetController {

	@Autowired
	private SimpleMapToSnomedWithCorrelationRefsetService refsetService;

	@Override
	protected String getRefsetType() {
		return Concepts.SIMPLE_MAP_WITH_CORRELATION_TO_SNOMEDCT_REFSET;
	}

	@Override
	protected String getFilenamePrefix() {
		return "MapRefset";
	}

	@Override
	protected RefsetUpdateService getRefsetService() {
		return refsetService;
	}
}
