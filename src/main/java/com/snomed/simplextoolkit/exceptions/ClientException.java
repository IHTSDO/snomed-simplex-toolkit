package com.snomed.simplextoolkit.exceptions;

import org.springframework.web.client.HttpStatusCodeException;

public class ClientException extends ServiceException {

	public ClientException(String message, HttpStatusCodeException httpStatusCodeException) {
		super(message, httpStatusCodeException);
	}

	@Override
	public synchronized HttpStatusCodeException getCause() {
		return (HttpStatusCodeException) super.getCause();
	}
}
