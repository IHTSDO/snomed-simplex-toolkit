package org.snomed.simplex.client.authoringservices;

import java.util.Objects;

public class APTask {
	private String key;
	private String projectKey;
	private String summary;
	private String status;
	private String branchPath;
	private APAssignee assignee;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public APAssignee getAssignee() {
		return assignee;
	}

	public void setAssignee(APAssignee assignee) {
		this.assignee = assignee;
	}

	public boolean isOpen() {
		if (status == null || status.isEmpty()) {
			return false;
		}

		String lowerCase = status.toLowerCase();
		boolean isPromoted = Objects.equals(lowerCase, "promoted");
		boolean isDeleted = Objects.equals(lowerCase, "deleted");
		boolean isCompleted = Objects.equals(lowerCase, "completed");
		boolean isAuto = lowerCase.startsWith("auto");

		return !isPromoted && !isDeleted && !isCompleted && !isAuto;
	}
}
