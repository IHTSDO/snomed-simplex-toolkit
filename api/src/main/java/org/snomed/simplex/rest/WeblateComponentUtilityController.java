package org.snomed.simplex.rest;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.exceptions.ServiceException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Weblate Util", description = "Tools for maintaining weblate")
@RequestMapping("api/weblate-util")
public class WeblateComponentUtilityController {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@PostMapping(value = "/create-component-csv", produces = "text/csv")
	public void createCollection(@RequestParam String valueSetEcl) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
//		snowstormClient.expandValueSet();
	}

}
