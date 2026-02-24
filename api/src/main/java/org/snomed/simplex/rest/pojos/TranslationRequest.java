package org.snomed.simplex.rest.pojos;

public class TranslationRequest {
	public static final String ASSIGNEE_USERNAME = "assigneeUsername";
	public static final String TASK_TITLE = "taskTitle";

	private String taskTitle;
	private String assigneeUsername;

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
}
