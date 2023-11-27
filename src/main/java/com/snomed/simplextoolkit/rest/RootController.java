package com.snomed.simplextoolkit.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@Tag(name = "Root controller")
@RequestMapping("api")
public class RootController {

	@GetMapping("/")
	@Operation(summary = "Root controller", hidden = true)
	public void root(HttpServletResponse response) throws IOException {
		response.sendRedirect("../swagger-ui.html");
	}

}
