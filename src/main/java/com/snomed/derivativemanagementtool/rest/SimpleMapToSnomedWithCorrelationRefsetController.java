package com.snomed.derivativemanagementtool.rest;

import com.snomed.derivativemanagementtool.domain.Concepts;
import com.snomed.derivativemanagementtool.service.RefsetUpdateService;
import com.snomed.derivativemanagementtool.service.SimpleMapToSnomedWithCorrelationRefsetService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/refsets/simple-map-to-snomed-with-correlation")
@Api(tags = "Simple Map to SNOMED with Correlation Refsets", description = "-")
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
