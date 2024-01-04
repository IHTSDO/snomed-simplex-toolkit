package org.snomed.simplex.rest;

import org.snomed.simplex.config.UiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "UI Configuration", description = "-")
@RequestMapping(path = "/api", produces={MediaType.APPLICATION_JSON_VALUE})
public class UiConfigurationController {

	@Autowired
	private UiConfiguration uiConfiguration;

	@Operation(summary="Retrieve configuration for the UI.")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "OK")})
	@RequestMapping(value="/ui-configuration", method= RequestMethod.GET)
	public UiConfiguration retrieveUiConfiguration() {
		return uiConfiguration;
	}

}
