package org.snomed.simplex.service.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ProgressMonitor;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ContentJob extends AsyncJob implements ProgressMonitor {

	private int recordsTotal;
	private int recordsProcessed;
	private String refsetId;
	private File inputFileCopy;
	private String inputFileOriginalName;
	private final Map<String, Object> parameters = new HashMap<>();

	public ContentJob(CodeSystem codeSystem, String display, String refsetId) {
		super(codeSystem, display);
		this.refsetId = refsetId;
	}

	public ContentJob addUpload(InputStream inputStream, String originalFilename) throws IOException {
		File tempFile = File.createTempFile("user-temp-file_" + getId(), "txt");
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			StreamUtils.copy(inputStream, out);
		}
		setInputFileCopy(tempFile);
		this.inputFileOriginalName = originalFilename;
		return this;
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

	public void addParameter(String key, Object value) {
		if (key == null || value == null) {
			return;
		}

		this.parameters.put(key, value);
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}

	public String getStringParameter(String key) {
		try {
			return (String) parameters.get(key);
		} catch (Exception e) {
			return null;
		}
	}
}
