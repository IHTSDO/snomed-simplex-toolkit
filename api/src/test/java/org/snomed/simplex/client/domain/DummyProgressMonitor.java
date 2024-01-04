package org.snomed.simplex.client.domain;

import org.snomed.simplex.service.ProgressMonitor;

public class DummyProgressMonitor implements ProgressMonitor {

	@Override
	public void incrementRecordsProcessed() {

	}

	@Override
	public void setRecordsTotal(int recordsTotal) {
	}

	@Override
	public void setRecordsProcessed(int recordsProcessed) {
	}

	@Override
	public void setProgressPercentageInsteadOfNumber(int progressPercentage) {
	}
}
