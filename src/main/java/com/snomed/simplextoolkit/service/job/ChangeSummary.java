package com.snomed.simplextoolkit.service.job;

public class ChangeSummary {

	private int added;
	private int updated;
	private int removed;
	private int newTotal;

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

	@Override
	public String toString() {
		return "ChangeSummary{" +
				"added=" + added +
				", updated=" + updated +
				", removed=" + removed +
				", newTotal=" + newTotal +
				'}';
	}
}
