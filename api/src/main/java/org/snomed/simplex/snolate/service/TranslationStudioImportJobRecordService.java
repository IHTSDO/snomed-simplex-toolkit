package org.snomed.simplex.snolate.service;

import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.service.job.TranslationStudioContentJob;
import org.snomed.simplex.snolate.domain.TranslationStudioImportJobRecord;
import org.snomed.simplex.snolate.domain.TranslationStudioImportJobRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TranslationStudioImportJobRecordService {

	private static final int DEFAULT_LIST_LIMIT = 50;

	private final TranslationStudioImportJobRecordRepository repository;

	public TranslationStudioImportJobRecordService(TranslationStudioImportJobRecordRepository repository) {
		this.repository = repository;
	}

	public void saveFromJob(AsyncJob job) {
		if (!(job instanceof TranslationStudioContentJob)) {
			return;
		}
		repository.save(toRecord(job));
	}

	public List<TranslationStudioImportJobRecord> listRecords(String codeSystem, String refsetId, int limit) {
		int pageSize = limit > 0 ? limit : DEFAULT_LIST_LIMIT;
		PageRequest pageRequest = PageRequest.of(0, pageSize);
		if (refsetId == null || refsetId.isBlank()) {
			return repository.findByCodesystemOrderByCreatedDesc(codeSystem, pageRequest).getContent();
		}
		return repository.findByCodesystemAndRefsetIdOrderByCreatedDesc(codeSystem, refsetId, pageRequest).getContent();
	}

	public Optional<TranslationStudioImportJobRecord> findById(String codeSystem, String jobId) {
		return repository.findByCodesystemAndId(codeSystem, jobId);
	}

	public AsyncJob toAsyncJob(TranslationStudioImportJobRecord jobRecord) {
		TranslationStudioContentJob job = new TranslationStudioContentJob(
				jobRecord.getCodesystem(), jobRecord.getDisplay(), jobRecord.getRefsetId(), jobRecord.getId(), jobRecord.getCreated());
		job.setUsername(jobRecord.getUsername());
		job.setStatus(jobRecord.getStatus());
		job.setErrorMessage(jobRecord.getErrorMessage());
		ChangeSummary changeSummary = new ChangeSummary();
		changeSummary.restoreImportCounts(
				jobRecord.getUpdated(),
				jobRecord.getSkippedNotFound(),
				jobRecord.getSkippedOutsideSet(),
				jobRecord.getSkippedNotFoundCodes());
		job.setChangeSummary(changeSummary);
		return job;
	}

	public void writeSkippedNotFoundCsv(TranslationStudioImportJobRecord jobRecord, OutputStream outputStream) throws IOException {
		writeSkippedNotFoundCsv(jobRecord.getSkippedNotFoundCodes(), jobRecord.getUsername(), outputStream);
	}

	public void writeSkippedNotFoundCsv(AsyncJob job, OutputStream outputStream) throws IOException {
		ChangeSummary changeSummary = job.getChangeSummary();
		if (changeSummary == null) {
			throw new IOException("No skipped not-found concept codes recorded for this import job.");
		}
		writeSkippedNotFoundCsv(changeSummary.getSkippedNotFoundCodes(), job.getUsername(), outputStream);
	}

	private static void writeSkippedNotFoundCsv(List<String> codes, String username, OutputStream outputStream) throws IOException {
		if (codes == null || codes.isEmpty()) {
			throw new IOException("No skipped not-found concept codes recorded for this import job.");
		}
		String user = username != null ? username : "";
		try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
			writer.write("conceptCode,username\n");
			for (String code : codes) {
				writer.write(escapeCsvField(code));
				writer.write(',');
				writer.write(escapeCsvField(user));
				writer.write('\n');
			}
		}
	}

	private static TranslationStudioImportJobRecord toRecord(AsyncJob job) {
		ContentJob contentJob = (ContentJob) job;
		TranslationStudioImportJobRecord jobRecord = new TranslationStudioImportJobRecord();
		jobRecord.setId(job.getId());
		jobRecord.setCodesystem(job.getCodeSystem());
		jobRecord.setRefsetId(contentJob.getRefsetId());
		jobRecord.setDisplay(job.getDisplay());
		jobRecord.setUsername(job.getUsername());
		jobRecord.setCreated(job.getCreated());
		jobRecord.setStatus(job.getStatus());
		jobRecord.setErrorMessage(job.getErrorMessage());
		ChangeSummary changeSummary = job.getChangeSummary();
		if (changeSummary != null) {
			jobRecord.setUpdated(changeSummary.getUpdated());
			jobRecord.setSkippedNotFound(changeSummary.getSkippedNotFound());
			jobRecord.setSkippedOutsideSet(changeSummary.getSkippedOutsideSet());
			jobRecord.setSkippedNotFoundCodes(new ArrayList<>(changeSummary.getSkippedNotFoundCodes()));
		}
		return jobRecord;
	}

	static String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0;
		if (!needsQuotes) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
