package org.snomed.simplex.service.job;

public class ChangeSummary {

	private int added;
	private int updated;
	private int removed;
	private int newTotal;
	private int skippedNotFound;
	private int skippedOutsideSet;

	public ChangeSummary() {
	}

	public ChangeSummary(int added, int updated, int removed, int newTotal) {
		this.added = added;
		this.updated = updated;
		this.removed = removed;
		this.newTotal = newTotal;
	}

	public void incrementAdded() {
		added++;
	}

	public void incrementUpdated() {
		updated++;
	}

	public void incrementRemoved() {
		removed++;
	}

	public void incrementSkippedNotFound() {
		skippedNotFound++;
	}

	public void incrementSkippedOutsideSet() {
		skippedOutsideSet++;
	}

	public int getAdded() {
		return added;
	}

	public int getUpdated() {
		return updated;
	}

	public int getRemoved() {
		return removed;
	}

	public int getNewTotal() {
		return newTotal;
	}

	public void setNewTotal(int newTotal) {
		this.newTotal = newTotal;
	}

	public int getSkippedNotFound() {
		return skippedNotFound;
	}

	public int getSkippedOutsideSet() {
		return skippedOutsideSet;
	}

	@Override
	public String toString() {
		return "ChangeSummary{" +
			"added=" + added +
			", updated=" + updated +
			", removed=" + removed +
			", newTotal=" + newTotal +
			", skippedNotFound=" + skippedNotFound +
			", skippedOutsideSet=" + skippedOutsideSet +
			'}';
	}
}
