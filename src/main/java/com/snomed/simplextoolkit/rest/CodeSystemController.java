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
import com.snomed.simplextoolkit.service.SecurityService;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

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

	@Autowired
	private SecurityService securityService;

	@GetMapping
	public Page<CodeSystem> getCodeSystems() throws ServiceException {
		List<CodeSystem> codeSystems = clientFactory.getClient().getCodeSystems();
		securityService.updateUserRolePermissionCache(codeSystems);
		// Filter out codesystems where the user has no role.
		codeSystems = codeSystems.stream().filter(codeSystem -> !codeSystem.getUserRoles().isEmpty()).toList();
		return new Page<>(codeSystems);
	}

	@GetMapping("{codeSystem}")
	@PostAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public CodeSystem getCodeSystemDetails(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem codeSystemForDisplay = snowstormClient.getCodeSystemForDisplay(codeSystem);
		securityService.updateUserRolePermissionCache(Collections.singletonList(codeSystemForDisplay));
		return codeSystemForDisplay;
	}

	@PostMapping
	@PreAuthorize("hasPermission('ADMIN', '')")
	public CodeSystem createCodeSystem(@RequestBody CreateCodeSystemRequest request) throws ServiceException {
		return codeSystemService.createCodeSystem(request.getName(), request.getShortName(), request.getNamespace(), request.isCreateModule(), request.getModuleName(),
				request.getModuleId());
	}

	@PostMapping("{codeSystem}/classify")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob createClassificationJob(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		if (theCodeSystem.isClassified()) {
			throw new ServiceException("This codesystem is already classified.");
		}

		return jobService.startExternalServiceJob(theCodeSystem, "Classify", asyncJob -> codeSystemService.classify(asyncJob));
	}

	@PostMapping("{codeSystem}/validate")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob startValidation(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return jobService.startExternalServiceJob(theCodeSystem, "Validate", asyncJob -> codeSystemService.validate(asyncJob));
	}

	@DeleteMapping("{codeSystem}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void deleteCodeSystem(@PathVariable String codeSystem) throws ServiceException {
		codeSystemService.deleteCodeSystem(codeSystem);
	}

	@PostMapping("{codeSystem}/working-branch")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void setBranchOverride(@PathVariable String codeSystem, @RequestBody SetBranchRequest request) throws ServiceException {
		SnowstormClient snowstormClient = clientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.setCodeSystemWorkingBranch(theCodeSystem, request.getBranchPath());
	}

}
