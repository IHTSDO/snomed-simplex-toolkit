package com.snomed.simpleextensiontoolkit.rest;

import com.snomed.simpleextensiontoolkit.client.SnowstormClientFactory;
import com.snomed.simpleextensiontoolkit.domain.CodeSystem;
import com.snomed.simpleextensiontoolkit.domain.Page;
import com.snomed.simpleextensiontoolkit.exceptions.ServiceException;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/codesystems")
@Api(tags = "Code Systems", description = "-")
public class CodeSystemController {

	@Autowired
	private SnowstormClientFactory clientFactory;

	@GetMapping
	public Page<CodeSystem> getCodeSystems() throws ServiceException {
		return clientFactory.getClient().getCodeSystems();
	}

}
