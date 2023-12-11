package com.snomed.simplextoolkit.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

import static java.lang.String.format;

public class AccessDeniedExceptionHandler implements AccessDeniedHandler {

	protected static final Logger LOGGER = LoggerFactory.getLogger(AccessDeniedExceptionHandler.class);

	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
		LOGGER.error("Request '{}' raised: " + exception.getMessage(), request.getRequestURL(), exception);
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(getErrorPayload(exception));
	}

	private String getErrorPayload(Exception exception) {
		return format("{\"error\":\"%s\", \"message\":\"%s\"}", HttpStatus.FORBIDDEN, exception.getMessage());
	}

}
