package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.service.job.JobType;
import org.snomed.simplex.service.job.TranslationStudioContentJob;
import org.snomed.simplex.service.test.TestTranslationStudioImportJobRecordRepository;
import org.snomed.simplex.snolate.domain.TranslationStudioImportJobRecord;
import org.snomed.simplex.snolate.service.TranslationStudioImportJobRecordService;
import org.snomed.simplex.domain.JobStatus;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ContentProcessingJobServiceTest {

	private static final String EDITION = "TEST";
	private static final String REFSET = "1000123";

	@Mock
	private SupportRegister supportRegister;

	@Mock
	private ActivityService activityService;

	private TestTranslationStudioImportJobRecordRepository importJobRecordRepository;
	private ContentProcessingJobService service;

	@BeforeEach
	void setUp() {
		importJobRecordRepository = new TestTranslationStudioImportJobRecordRepository();
		service = new ContentProcessingJobService(1, supportRegister, activityService,
				new TranslationStudioImportJobRecordService(importJobRecordRepository));
	}

	@Test
	void listJobs_byRefsetExcludesTranslationStudioJobs() throws Exception {
		registerJob(new ContentJob(codeSystem(), "Snowstorm translation upload", REFSET));
		registerJob(new TranslationStudioContentJob(codeSystem(), "Translation Studio set CSV import", REFSET));

		List<AsyncJob> jobs = service.listJobs(EDITION, REFSET, null);

		assertThat(jobs).hasSize(1);
		assertThat(jobs.get(0).getDisplay()).isEqualTo("Snowstorm translation upload");
	}

	@Test
	void listJobs_byTranslationStudioTypeIncludesTranslationStudioJobs() throws Exception {
		registerJob(new ContentJob(codeSystem(), "Snowstorm translation upload", REFSET));
		registerJob(new TranslationStudioContentJob(codeSystem(), "Translation Studio set CSV import", REFSET));

		List<AsyncJob> jobs = service.listJobs(EDITION, null, JobType.TRANSLATION_STUDIO);

		assertThat(jobs).hasSize(1);
		assertThat(jobs.get(0).getJobType()).isEqualTo(JobType.TRANSLATION_STUDIO);
	}

	@Test
	void listJobs_byTranslationStudioTypeAndRefsetFiltersBoth() throws Exception {
		registerJob(new TranslationStudioContentJob(codeSystem(), "Matching import", REFSET));
		registerJob(new TranslationStudioContentJob(codeSystem(), "Other language import", "9999999"));

		List<AsyncJob> jobs = service.listJobs(EDITION, REFSET, JobType.TRANSLATION_STUDIO);

		assertThat(jobs).hasSize(1);
		assertThat(jobs.get(0).getDisplay()).isEqualTo("Matching import");
	}

	@Test
	void listJobs_mergesPersistedTranslationStudioHistory() throws Exception {
		TranslationStudioContentJob inMemoryJob = new TranslationStudioContentJob(codeSystem(), "In-memory import", REFSET);
		inMemoryJob.setStatus(JobStatus.COMPLETE);
		registerJob(inMemoryJob);

		TranslationStudioImportJobRecord persisted = new TranslationStudioImportJobRecord();
		persisted.setId("persisted-job");
		persisted.setCodesystem(EDITION);
		persisted.setRefsetId(REFSET);
		persisted.setDisplay("Persisted import");
		persisted.setUsername("test.user");
		persisted.setCreated(new java.util.Date(0L));
		persisted.setStatus(JobStatus.COMPLETE);
		importJobRecordRepository.save(persisted);

		List<AsyncJob> jobs = service.listJobs(EDITION, null, JobType.TRANSLATION_STUDIO);

		assertThat(jobs).hasSize(2);
		assertThat(jobs).extracting(AsyncJob::getId).contains(inMemoryJob.getId(), "persisted-job");
	}

	@Test
	void getAsyncJob_returnsPersistedTranslationStudioJobWhenNotInMemory() {
		TranslationStudioImportJobRecord persisted = new TranslationStudioImportJobRecord();
		persisted.setId("persisted-job");
		persisted.setCodesystem(EDITION);
		persisted.setRefsetId(REFSET);
		persisted.setDisplay("Persisted import");
		persisted.setUsername("test.user");
		persisted.setCreated(new java.util.Date(0L));
		persisted.setStatus(JobStatus.COMPLETE);
		ChangeSummary changeSummary = new ChangeSummary();
		changeSummary.recordSkippedNotFound("100");
		persisted.setUpdated(changeSummary.getUpdated());
		persisted.setSkippedNotFound(changeSummary.getSkippedNotFound());
		persisted.setSkippedOutsideSet(changeSummary.getSkippedOutsideSet());
		persisted.setSkippedNotFoundCodes(changeSummary.getSkippedNotFoundCodes());
		importJobRecordRepository.save(persisted);

		AsyncJob job = service.getAsyncJob(EDITION, "persisted-job");

		assertThat(job.getDisplay()).isEqualTo("Persisted import");
		assertThat(job.getUsername()).isEqualTo("test.user");
		assertThat(job.getChangeSummary().getSkippedNotFoundCodes()).containsExactly("100");
	}

	private static CodeSystem codeSystem() {
		return new CodeSystem("Test", EDITION, "branch");
	}

	@SuppressWarnings("unchecked")
	private void registerJob(AsyncJob job) throws Exception {
		Field field = ContentProcessingJobService.class.getDeclaredField("codeSystemJobs");
		field.setAccessible(true);
		Map<String, Map<String, AsyncJob>> map = (Map<String, Map<String, AsyncJob>>) field.get(service);
		map.computeIfAbsent(EDITION, key -> new LinkedHashMap<>()).put(job.getId(), job);
	}
}
