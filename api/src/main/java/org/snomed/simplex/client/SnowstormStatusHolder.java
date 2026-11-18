package org.snomed.simplex.client;

/**
 * Status and message of an asynchronous Snowstorm job.
 */
final class SnowstormStatusHolder {

	private String status;
	private String message;

	public String getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}
