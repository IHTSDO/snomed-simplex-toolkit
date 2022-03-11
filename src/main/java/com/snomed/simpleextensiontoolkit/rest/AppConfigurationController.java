package com.snomed.simpleextensiontoolkit.rest;

import com.snomed.simpleextensiontoolkit.domain.CodeSystemProperties;
import com.snomed.simpleextensiontoolkit.exceptions.ServiceException;
import com.snomed.simpleextensiontoolkit.service.CodeSystemConfigService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/app-config")
@Api(tags = "App Config", description = "-")
public class AppConfigurationController {

	@Autowired
	private CodeSystemConfigService codeSystemConfigService;

	@GetMapping
	public CodeSystemProperties getConfig() throws ServiceException {
		return codeSystemConfigService.getConfig();
	}

	@PostMapping
	public CodeSystemProperties saveConfig(@RequestBody CodeSystemProperties codeSystemProperties) throws ServiceException {
		codeSystemConfigService.saveConfig(codeSystemProperties);
		return codeSystemConfigService.getConfig();
	}
}
