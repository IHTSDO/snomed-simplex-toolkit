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

	public AsyncJob toAsyncJob(TranslationStudioImportJobRecord record) {
		TranslationStudioContentJob job = new TranslationStudioContentJob(
				record.getCodesystem(), record.getDisplay(), record.getRefsetId(), record.getId(), record.getCreated());
		job.setUsername(record.getUsername());
		job.setStatus(record.getStatus());
		job.setErrorMessage(record.getErrorMessage());
		ChangeSummary changeSummary = new ChangeSummary();
		changeSummary.restoreImportCounts(
				record.getUpdated(),
				record.getSkippedNotFound(),
				record.getSkippedOutsideSet(),
				record.getSkippedNotFoundCodes());
		job.setChangeSummary(changeSummary);
		return job;
	}

	public void writeSkippedNotFoundCsv(TranslationStudioImportJobRecord record, OutputStream outputStream) throws IOException {
		writeSkippedNotFoundCsv(record.getSkippedNotFoundCodes(), record.getUsername(), outputStream);
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
		TranslationStudioImportJobRecord record = new TranslationStudioImportJobRecord();
		record.setId(job.getId());
		record.setCodesystem(job.getCodeSystem());
		record.setRefsetId(contentJob.getRefsetId());
		record.setDisplay(job.getDisplay());
		record.setUsername(job.getUsername());
		record.setCreated(job.getCreated());
		record.setStatus(job.getStatus());
		record.setErrorMessage(job.getErrorMessage());
		ChangeSummary changeSummary = job.getChangeSummary();
		if (changeSummary != null) {
			record.setUpdated(changeSummary.getUpdated());
			record.setSkippedNotFound(changeSummary.getSkippedNotFound());
			record.setSkippedOutsideSet(changeSummary.getSkippedOutsideSet());
			record.setSkippedNotFoundCodes(new ArrayList<>(changeSummary.getSkippedNotFoundCodes()));
		}
		return record;
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
