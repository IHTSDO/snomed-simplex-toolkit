package com.snomed.simplextoolkit.exceptions;

import org.springframework.web.client.HttpStatusCodeException;

public class HTTPClientException extends ServiceException {

	public HTTPClientException(String message, HttpStatusCodeException httpStatusCodeException) {
		super(message, httpStatusCodeException);
	}

	@Override
	public synchronized HttpStatusCodeException getCause() {
		return (HttpStatusCodeException) super.getCause();
	}
}