package com.snomed.simplextoolkit.domain;

public interface ProgressMonitor {

	void setRecordsTotal(int recordsTotal);

	void setRecordsProcessed(int recordsProcessed);
}
