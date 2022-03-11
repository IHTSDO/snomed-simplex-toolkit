package com.snomed.simpleextensiontoolkit.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("api")
@Api(hidden = true)
public class RootController {

	@GetMapping("/")
	@ApiOperation(value = "Root controller", hidden = true)
	public void root(HttpServletResponse response) throws IOException {
		response.sendRedirect("swagger-ui.html");
	}

}
