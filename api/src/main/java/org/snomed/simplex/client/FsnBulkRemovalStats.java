package org.snomed.simplex.client;

public class FsnBulkRemovalStats {

	private int conceptsUpdated;
	private int fsnInactivated;
	private int fsnDeleted;
	private int fsnSkipped;

	public void add(FsnBulkRemovalStats other) {
		conceptsUpdated += other.conceptsUpdated;
		fsnInactivated += other.fsnInactivated;
		fsnDeleted += other.fsnDeleted;
		fsnSkipped += other.fsnSkipped;
	}

	public void incrementConceptsUpdated() {
		conceptsUpdated++;
	}

	public void incrementFsnInactivated() {
		fsnInactivated++;
	}

	public void incrementFsnDeleted() {
		fsnDeleted++;
	}

	public void incrementFsnSkipped() {
		fsnSkipped++;
	}

	public int getConceptsUpdated() {
		return conceptsUpdated;
	}

	public int getFsnInactivated() {
		return fsnInactivated;
	}

	public int getFsnDeleted() {
		return fsnDeleted;
	}

	public int getFsnSkipped() {
		return fsnSkipped;
	}
}
