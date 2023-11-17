package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.client.domain.Concepts;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.JobService;
import com.snomed.simplextoolkit.service.RefsetToolSubsetReader;
import com.snomed.simplextoolkit.service.RefsetUpdateService;
import com.snomed.simplextoolkit.service.SimpleRefsetService;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple")
@Api(tags = "Simple Refsets", description = "-")
public class SimpleRefsetController extends AbstractRefsetController {

	@Autowired
	private SimpleRefsetService simpleRefsetService;

	@Autowired
	private JobService jobService;

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

	@PutMapping(path = "{refsetId}/refset-tool", consumes = "multipart/form-data")
	public AsyncJob uploadRefsetToolSubset(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			UriComponentsBuilder uriComponentBuilder) throws ServiceException, IOException {

		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		return jobService.queueRefsetContentJob(codeSystem, "Subset upload (Refset Tool)", file.getInputStream(), refsetId,
				asyncJob -> getRefsetService().updateRefsetViaCustomFile(refsetId, new RefsetToolSubsetReader(asyncJob.getInputStream()), theCodeSystem, asyncJob));
	}

}
