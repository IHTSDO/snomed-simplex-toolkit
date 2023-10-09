package com.snomed.simplextoolkit.service;

public interface ProgressMonitor {

	void setRecordsTotal(int recordsTotal);

	void setRecordsProcessed(int recordsProcessed);

	void setProgressPercentageInsteadOfNumber(int progressPercentage);
}
