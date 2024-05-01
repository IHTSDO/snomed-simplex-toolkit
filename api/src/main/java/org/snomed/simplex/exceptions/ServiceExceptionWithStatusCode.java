package org.snomed.simplex.exceptions;

import org.springframework.http.HttpStatus;

public class ServiceExceptionWithStatusCode extends ServiceException {

	private final int statusCode;

	public ServiceExceptionWithStatusCode(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public ServiceExceptionWithStatusCode(String message, HttpStatus statusCode) {
		this(message, statusCode.value());
	}

	public int getStatusCode() {
		return statusCode;
	}
}
