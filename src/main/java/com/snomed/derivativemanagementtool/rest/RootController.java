package com.snomed.derivativemanagementtool.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.RequestHandler;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("api")
public class RootController {

	@GetMapping("/")
	public void root(HttpServletResponse response) throws IOException {
		response.sendRedirect("swagger-ui.html");
	}

}
