package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.TranslationStudioContentJob;
import org.snomed.simplex.service.test.TestTranslationStudioImportJobRecordRepository;
import org.snomed.simplex.snolate.domain.TranslationStudioImportJobRecord;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationStudioImportJobRecordServiceTest {

	private static final String EDITION = "SNOMEDCT-TEST";
	private static final String REFSET = "1000123";

	private TestTranslationStudioImportJobRecordRepository repository;
	private TranslationStudioImportJobRecordService service;

	@BeforeEach
	void setUp() {
		repository = new TestTranslationStudioImportJobRecordRepository();
		service = new TranslationStudioImportJobRecordService(repository);
	}

	@Test
	void saveFromJob_persistsTranslationStudioImportJob() {
		TranslationStudioContentJob job = new TranslationStudioContentJob(EDITION, "Import", REFSET, "job-1", new Date());
		job.setUsername("test.user");
		job.setStatus(JobStatus.COMPLETE);
		ChangeSummary changeSummary = new ChangeSummary();
		changeSummary.recordSkippedNotFound("100");
		changeSummary.recordSkippedNotFound("200");
		job.setChangeSummary(changeSummary);

		service.saveFromJob(job);

		assertThat(repository.getRecords()).hasSize(1);
		TranslationStudioImportJobRecord record = repository.getRecords().get(0);
		assertThat(record.getUsername()).isEqualTo("test.user");
		assertThat(record.getSkippedNotFoundCodes()).containsExactly("100", "200");
	}

	@Test
	void toAsyncJob_restoresPersistedJobFields() {
		TranslationStudioImportJobRecord record = new TranslationStudioImportJobRecord();
		record.setId("job-1");
		record.setCodesystem(EDITION);
		record.setRefsetId(REFSET);
		record.setDisplay("Import");
		record.setUsername("test.user");
		record.setCreated(new Date(1_000L));
		record.setStatus(JobStatus.COMPLETE);
		record.setUpdated(3);
		record.setSkippedNotFound(2);
		record.setSkippedOutsideSet(1);
		record.setSkippedNotFoundCodes(List.of("100", "200"));

		var job = service.toAsyncJob(record);

		assertThat(job.getId()).isEqualTo("job-1");
		assertThat(job.getUsername()).isEqualTo("test.user");
		assertThat(job.getChangeSummary().getUpdated()).isEqualTo(3);
		assertThat(job.getChangeSummary().getSkippedNotFound()).isEqualTo(2);
		assertThat(job.getChangeSummary().getSkippedOutsideSet()).isEqualTo(1);
		assertThat(job.getChangeSummary().getSkippedNotFoundCodes()).containsExactly("100", "200");
	}

	@Test
	void writeSkippedNotFoundCsv_writesConceptCodeAndUsernameColumns() throws Exception {
		TranslationStudioImportJobRecord record = new TranslationStudioImportJobRecord();
		record.setUsername("test.user");
		record.setSkippedNotFoundCodes(List.of("100", "200"));

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		service.writeSkippedNotFoundCsv(record, outputStream);

		assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("""
				conceptCode,username
				100,test.user
				200,test.user
				""");
	}

	@Test
	void writeSkippedNotFoundCsv_rejectsEmptyCodeList() {
		TranslationStudioImportJobRecord record = new TranslationStudioImportJobRecord();
		record.setUsername("test.user");
		record.setSkippedNotFoundCodes(List.of());

		assertThatThrownBy(() -> service.writeSkippedNotFoundCsv(record, new ByteArrayOutputStream()))
				.hasMessageContaining("No skipped not-found concept codes");
	}
}
