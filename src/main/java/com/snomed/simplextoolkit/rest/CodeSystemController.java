package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.domain.CodeSystem;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.CreateCodeSystemRequest;
import com.snomed.simplextoolkit.service.CodeSystemService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/codesystems")
@Api(tags = "Code Systems", description = "-")
public class CodeSystemController {

	@Autowired
	private SnowstormClientFactory clientFactory;

	@Autowired
	private CodeSystemService codeSystemService;

	@GetMapping
	public Page<CodeSystem> getCodeSystems() throws ServiceException {
		return new Page<>(clientFactory.getClient().getCodeSystems());
	}

	@GetMapping("{codeSystem}")
	public CodeSystem getCodeSystemDetails(@PathVariable String codeSystem) throws ServiceException {
		return clientFactory.getClient().getCodeSystemForDisplay(codeSystem);
	}

	@PostMapping
	public CodeSystem createCodeSystem(@RequestBody CreateCodeSystemRequest request) throws ServiceException {
		return codeSystemService.createCodeSystem(request.getName(), request.getShortName(), request.getNamespace(), request.isCreateModule(), request.getModuleName(),
				request.getModuleId());
	}

	@DeleteMapping("{codeSystem}")
	public void deleteCodeSystem(@PathVariable String codeSystem) throws ServiceException {
		codeSystemService.deleteCodeSystem(codeSystem);
	}

}
