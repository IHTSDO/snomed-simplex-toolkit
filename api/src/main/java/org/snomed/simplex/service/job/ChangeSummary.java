package org.snomed.simplex.service.job;

import java.util.ArrayList;
import java.util.List;

public class ChangeSummary {

	public static final int MAX_SKIPPED_NOT_FOUND_CODES = 500;

	private int added;
	private int updated;
	private int removed;
	private int newTotal;
	private int skippedNotFound;
	private int skippedOutsideSet;
	private List<String> skippedNotFoundCodes = new ArrayList<>();

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

	public void recordSkippedNotFound(String conceptCode) {
		skippedNotFound++;
		if (conceptCode != null && !conceptCode.isBlank()
				&& skippedNotFoundCodes.size() < MAX_SKIPPED_NOT_FOUND_CODES
				&& !skippedNotFoundCodes.contains(conceptCode)) {
			skippedNotFoundCodes.add(conceptCode);
		}
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

	public List<String> getSkippedNotFoundCodes() {
		return skippedNotFoundCodes;
	}

	public void setSkippedNotFoundCodes(List<String> skippedNotFoundCodes) {
		this.skippedNotFoundCodes = skippedNotFoundCodes != null ? skippedNotFoundCodes : new ArrayList<>();
	}

	public void restoreImportCounts(int updated, int skippedNotFound, int skippedOutsideSet, List<String> skippedNotFoundCodes) {
		this.updated = updated;
		this.skippedNotFound = skippedNotFound;
		this.skippedOutsideSet = skippedOutsideSet;
		setSkippedNotFoundCodes(skippedNotFoundCodes);
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
			", skippedNotFoundCodes=" + skippedNotFoundCodes.size() +
			'}';
	}
}
