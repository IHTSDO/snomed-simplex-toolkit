package com.snomed.derivativemanagementtool.rest;

import com.snomed.derivativemanagementtool.domain.Concepts;
import com.snomed.derivativemanagementtool.service.RefsetUpdateService;
import com.snomed.derivativemanagementtool.service.SimpleRefsetService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/refsets/simple")
@Api(tags = "Simple Refsets", description = "-")
public class SimpleRefsetController extends AbstractRefsetController {

	@Autowired
	private SimpleRefsetService simpleRefsetService;

	@Override
	protected String getRefsetType() {
		return Concepts.SIMPLE_TYPE_REFSET;
	}

	@Override
	protected String getFilenamePrefix() {
		return "SimpleRefset";
	}

	@Override
	protected RefsetUpdateService getRefsetService() {
		return simpleRefsetService;
	}
}
