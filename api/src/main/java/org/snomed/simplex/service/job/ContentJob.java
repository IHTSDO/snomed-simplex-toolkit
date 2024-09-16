package org.snomed.simplex.service.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ProgressMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class ContentJob extends AsyncJob implements ProgressMonitor {

	private int recordsTotal;
	private int recordsProcessed;
	private String refsetId;
	private File inputFileCopy;
	private String inputFileOriginalName;

	public ContentJob(String codeSystem, String display, String refsetId) {
		super(codeSystem, display);
		this.refsetId = refsetId;
	}

	@Override
	public void incrementRecordsProcessed() {
		recordsProcessed++;
	}

	@Override
	public JobType getJobType() {
		return JobType.CONCEPT_CHANGE;
	}

	public int getRecordsTotal() {
		return recordsTotal;
	}

	@Override
	public void setRecordsTotal(int recordsTotal) {
		this.recordsTotal = recordsTotal;
	}

	public int getRecordsProcessed() {
		return recordsProcessed;
	}

	@Override
	public void setRecordsProcessed(int recordsProcessed) {
		this.recordsProcessed = recordsProcessed;
	}

	@Override
	public void setProgressPercentageInsteadOfNumber(int progressPercentage) {
		recordsProcessed = Math.round((recordsTotal * progressPercentage) / 100f);
	}

	public void setInputFileCopy(File tempFile) {
		this.inputFileCopy = tempFile;
	}

	@JsonIgnore
	public File getInputFileCopy() {
		return inputFileCopy;
	}

	@JsonIgnore
	public InputStream getInputStream() throws ServiceException {
		try {
			return new FileInputStream(inputFileCopy);
		} catch (FileNotFoundException e) {
			throw new ServiceException("Job input stream not found.", e);
		}
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public void setInputFileOriginalName(String inputFileOriginalName) {
		this.inputFileOriginalName = inputFileOriginalName;
	}

	public String getInputFileOriginalName() {
		return inputFileOriginalName;
	}
}
