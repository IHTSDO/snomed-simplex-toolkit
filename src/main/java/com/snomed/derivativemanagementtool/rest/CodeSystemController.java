package com.snomed.derivativemanagementtool.rest;

import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.domain.CodeSystem;
import com.snomed.derivativemanagementtool.domain.Page;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import com.snomed.derivativemanagementtool.service.CodeSystemConfigService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/codesystems")
@Api(tags = "Code Systems", description = "-")
public class CodeSystemController {

	@Autowired
	private CodeSystemConfigService codeSystemConfigService;

	@GetMapping
	public Page<CodeSystem> getCodeSystems() throws ServiceException {
		return getClient().getCodeSystems();
	}

	private SnowstormClient getClient() throws ServiceException {
		return codeSystemConfigService.getSnowstormClient();
	}

}
