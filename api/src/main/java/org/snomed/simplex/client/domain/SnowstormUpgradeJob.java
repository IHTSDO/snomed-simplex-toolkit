package org.snomed.simplex.client.domain;

public class SnowstormUpgradeJob {

	public enum Status {
		RUNNING, FAILED, COMPLETED
	}

	private Status status;
	private String errorMessage;

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
