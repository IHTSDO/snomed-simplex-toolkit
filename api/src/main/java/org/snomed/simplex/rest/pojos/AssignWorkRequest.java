package org.snomed.simplex.rest.pojos;

import java.util.List;

public class AssignWorkRequest {
    private List<WorkAssignment> assignments;

    public AssignWorkRequest() {
    }

    public AssignWorkRequest(List<WorkAssignment> assignments) {
        this.assignments = assignments;
    }

    public List<WorkAssignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<WorkAssignment> assignments) {
        this.assignments = assignments;
    }

    public static class WorkAssignment {
        private String username;
        private int percentage;

        public WorkAssignment() {
        }

        public WorkAssignment(String username, int percentage) {
            this.username = username;
            this.percentage = percentage;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public int getPercentage() {
            return percentage;
        }

        public void setPercentage(int percentage) {
            this.percentage = percentage;
        }
    }
}
