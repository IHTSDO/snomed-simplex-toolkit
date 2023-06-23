package com.snomed.simplextoolkit.service;

public class ChangeSummary {

	private final int added;
	private final int updated;
	private final int removed;
	private final int newTotal;

	public ChangeSummary(int added, int updated, int removed, int newTotal) {
		this.added = added;
		this.updated = updated;
		this.removed = removed;
		this.newTotal = newTotal;
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
