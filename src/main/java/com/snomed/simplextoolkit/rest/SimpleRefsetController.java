package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.domain.Concepts;
import com.snomed.simplextoolkit.service.RefsetUpdateService;
import com.snomed.simplextoolkit.service.SimpleRefsetService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple")
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
