package org.snomed.simplex.exceptions;

public class ServiceExceptionWithStatusCode extends ServiceException {

	private final int statusCode;

	public ServiceExceptionWithStatusCode(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
