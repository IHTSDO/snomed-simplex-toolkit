package org.snomed.simplex.rest.pojos;

public class APTaskRequest {
	public static final String ASSIGNEE_USERNAME = "assigneeUsername";
	public static final String TASK_TITLE = "taskTitle";
	public static final String INCLUDE_READY_FOR_REVIEW = "includeReadyForReview";

	private String taskTitle;
	private String assigneeUsername;
	private Boolean includeReadyForReview;

	public String getTaskTitle() {
		return taskTitle;
	}

	public void setTaskTitle(String taskTitle) {
		this.taskTitle = taskTitle;
	}

	public String getAssigneeUsername() {
		return assigneeUsername;
	}

	public void setAssigneeUsername(String assigneeUsername) {
		this.assigneeUsername = assigneeUsername;
	}

	public Boolean getIncludeReadyForReview() {
		return includeReadyForReview;
	}

	public void setIncludeReadyForReview(Boolean includeReadyForReview) {
		this.includeReadyForReview = includeReadyForReview;
	}

	public boolean isIncludeReadyForReviewOrDefault() {
		return includeReadyForReview == null || includeReadyForReview;
	}
}
