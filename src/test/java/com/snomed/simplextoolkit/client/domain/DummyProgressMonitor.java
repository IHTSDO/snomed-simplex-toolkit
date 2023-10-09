package com.snomed.simplextoolkit.client.domain;

import com.snomed.simplextoolkit.service.ProgressMonitor;

public class DummyProgressMonitor implements ProgressMonitor {
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
