package org.snomed.simplex.rest;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.RefsetToolSubsetReader;
import org.snomed.simplex.service.RefsetUpdateService;
import org.snomed.simplex.service.SimpleRefsetService;
import org.snomed.simplex.service.job.AsyncJob;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@RestController
@RequestMapping("api/{codeSystem}/refsets/simple")
@Tag(name = "Simple Refsets", description = "-")
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
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadRefsetToolSubset(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			UriComponentsBuilder uriComponentBuilder) throws ServiceException, IOException {

		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		return jobService.queueContentJob(codeSystem, "Subset upload (Refset Tool)", file.getInputStream(), refsetId,
				asyncJob -> getRefsetService().updateRefsetViaCustomFile(refsetId, new RefsetToolSubsetReader(asyncJob.getInputStream()), theCodeSystem, asyncJob));
	}

}
