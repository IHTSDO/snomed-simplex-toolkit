package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.CreateCodeSystemRequest;
import com.snomed.simplextoolkit.rest.pojos.SetBranchRequest;
import com.snomed.simplextoolkit.service.CodeSystemService;
import com.snomed.simplextoolkit.service.JobService;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/codesystems")
@Tag(name = "Code Systems", description = "-")
public class CodeSystemController {

	@Autowired
	private SnowstormClientFactory clientFactory;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private JobService jobService;

	@GetMapping
	public Page<CodeSystem> getCodeSystems() throws ServiceException {
		return new Page<>(clientFactory.getClient().getCodeSystems());
	}

	@GetMapping("{codeSystem}")
	public CodeSystem getCodeSystemDetails(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		return snowstormClient.getCodeSystemForDisplay(codeSystem);
	}

	@PostMapping
	public CodeSystem createCodeSystem(@RequestBody CreateCodeSystemRequest request) throws ServiceException {
		return codeSystemService.createCodeSystem(request.getName(), request.getShortName(), request.getNamespace(), request.isCreateModule(), request.getModuleName(),
				request.getModuleId());
	}

	@PostMapping("{codeSystem}/classify")
	public AsyncJob createClassificationJob(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		if (theCodeSystem.isClassified()) {
			throw new ServiceException("This codesystem is already classified.");
		}

		return jobService.startExternalServiceJob(theCodeSystem, "Classify", asyncJob -> codeSystemService.classify(asyncJob));
	}

	@PostMapping("{codeSystem}/validate")
	public AsyncJob startValidation(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return jobService.startExternalServiceJob(theCodeSystem, "Validate", asyncJob -> codeSystemService.validate(asyncJob));
	}

	@DeleteMapping("{codeSystem}")
	public void deleteCodeSystem(@PathVariable String codeSystem) throws ServiceException {
		codeSystemService.deleteCodeSystem(codeSystem);
	}

	@PostMapping("{codeSystem}/working-branch")
	public void setBranchOverride(@PathVariable String codeSystem, @RequestBody SetBranchRequest request) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.setCodeSystemWorkingBranch(theCodeSystem, request.getBranchPath());
	}

}
