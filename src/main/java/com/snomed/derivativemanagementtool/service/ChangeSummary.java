package com.snomed.derivativemanagementtool.service;

public class ChangeSummary {

	private final int added;
	private final int removed;
	private final int newTotal;

	public ChangeSummary(int added, int removed, int newTotal) {
		this.added = added;
		this.removed = removed;
		this.newTotal = newTotal;
	}

	public int getAdded() {
		return added;
	}

	public int getRemoved() {
		return removed;
	}

	public int getNewTotal() {
		return newTotal;
	}
}
