package org.snomed.simplex.service;

public interface ProgressMonitor {

	void setRecordsTotal(int recordsTotal);

	void setRecordsProcessed(int recordsProcessed);

	void incrementRecordsProcessed();

	void setProgressPercentageInsteadOfNumber(int progressPercentage);
}
