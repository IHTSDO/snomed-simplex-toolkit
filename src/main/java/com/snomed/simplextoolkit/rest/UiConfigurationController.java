package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.config.UiConfiguration;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Api(tags = "UI Configuration", description = "-")
@RequestMapping(path = "/api", produces={MediaType.APPLICATION_JSON_VALUE})
public class UiConfigurationController {

	@Autowired
	private UiConfiguration uiConfiguration;

	@ApiOperation(value="Retrieve configuration for the UI.")
	@ApiResponses({@ApiResponse(code = 200, message = "OK")})
	@RequestMapping(value="/ui-configuration", method= RequestMethod.GET)
	public UiConfiguration retrieveUiConfiguration() {
		return uiConfiguration;
	}

}
