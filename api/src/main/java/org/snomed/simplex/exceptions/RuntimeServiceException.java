package org.snomed.simplex.exceptions;

public class RuntimeServiceException extends RuntimeException {
	public RuntimeServiceException(String message) {
		super(message);
	}

	public RuntimeServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
