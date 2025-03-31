package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.job.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ContentProcessingJobService {

	private final Map<String, Map<String, AsyncJob>> codeSystemJobs;
	private final ExecutorService jobExecutorService;
	private final SupportRegister supportRegister;
	private final ActivityService activityService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ContentProcessingJobService(@Value("${job.concurrent.threads}") int nThreads, SupportRegister supportRegister, ActivityService activityService) {
		codeSystemJobs = new HashMap<>();
		jobExecutorService = Executors.newFixedThreadPool(nThreads);
		this.supportRegister = supportRegister;
		this.activityService = activityService;
	}

	public AsyncJob queueContentJob(CodeSystem codeSystem, String display, InputStream jobInputStream, String originalFilename, String refsetId,
			Activity activity, AsyncFunction<ContentJob> function) throws IOException {

		activity.setComponentId(refsetId);
		ContentJob asyncJob = new ContentJob(codeSystem, display, refsetId);
		File tempFile = File.createTempFile("user-temp-file_" + asyncJob.getId(), "txt");
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			StreamUtils.copy(jobInputStream, out);
		}
		asyncJob.setInputFileCopy(tempFile);
		asyncJob.setInputFileOriginalName(originalFilename);

		return doQueueJob(codeSystem, function, asyncJob, activity, () -> {
			if (!tempFile.delete()) {
				logger.info("Failed to delete temp file {}", tempFile.getAbsoluteFile());
			}
		});
	}

	private ContentJob doQueueJob(CodeSystem codeSystem, AsyncFunction<ContentJob> function, ContentJob asyncJob, Activity activity,
			Runnable onCompleteRunnable) {

		// Add job to thread limited executor service to be run when there is capacity
		final SecurityContext userSecurityContext = SecurityContextHolder.getContext();

		asyncJob.setStatus(JobStatus.QUEUED);

		try {
			activityService.addQueuedContentActivity(activity, asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemError(asyncJob, "Failed to record activity.", e);
		}

		jobExecutorService.submit(() -> {
			SecurityContextHolder.setContext(userSecurityContext);
			try {
				asyncJob.setStatus(JobStatus.IN_PROGRESS);
				ChangeSummary changeSummary = function.run(asyncJob);
				asyncJob.setChangeSummary(changeSummary);
				asyncJob.setStatus(JobStatus.COMPLETE);
			} catch (ServiceException e) {
				handleServiceException(asyncJob, activity, e);
			} catch (Exception e) {
				ServiceException serviceException = new ServiceException("Unexpected error.", e);
				activity.exception(serviceException);
				supportRegister.handleSystemError(asyncJob, "Unexpected error.", serviceException);
			} finally {
				activityService.endAsynchronousActivity(activity);
				if (onCompleteRunnable != null) {
					onCompleteRunnable.run();
				}
			}
		});

		codeSystemJobs.computeIfAbsent(codeSystem.getShortName(), i -> new LinkedHashMap<>()).put(asyncJob.getId(), asyncJob);
		return asyncJob;
	}

	private void handleServiceException(ContentJob asyncJob, Activity activity, ServiceException e) {
		if (e instanceof ServiceExceptionWithStatusCode errorWithCode && errorWithCode.getJobStatus() != null) {
			asyncJob.setStatus(errorWithCode.getJobStatus());
		} else {
			if (asyncJob.getStatus() == null || asyncJob.getStatus() == JobStatus.IN_PROGRESS) {
				asyncJob.setStatus(JobStatus.SYSTEM_ERROR);
			}
		}
		asyncJob.setServiceException(e);
		activity.exception(e);
		JobStatus status = asyncJob.getStatus();
		if (status == JobStatus.SYSTEM_ERROR) {
			supportRegister.handleSystemError(asyncJob, e.getMessage(), e);
		} else if (status == JobStatus.TECHNICAL_CONTENT_ISSUE) {
			supportRegister.handleTechnicalContentIssue(asyncJob, e.getMessage());
		}
	}

	public AsyncJob getAsyncJob(String codeSystem, String jobId) {
		synchronized (codeSystemJobs) {
			return codeSystemJobs.getOrDefault(codeSystem, Collections.emptyMap()).get(jobId);
		}
	}

	public List<AsyncJob> listJobs(String codeSystem, String refsetId, JobType jobType) {
		List<AsyncJob> jobs;
		if (codeSystem != null) {
			jobs = new ArrayList<>(codeSystemJobs.getOrDefault(codeSystem, Collections.emptyMap()).values());
		} else {
			jobs = codeSystemJobs.values().stream()
					.flatMap(value -> value.values().stream())
					.toList();
		}
		return jobs.stream()
				.filter(job -> jobType == null || job.getJobType() == jobType)
				.filter(job -> refsetId == null || (job instanceof ContentJob contentJob && refsetId.equals(contentJob.getRefsetId())))
				.sorted(Comparator.comparing(AsyncJob::getCreated).reversed())
				.toList();
	}

	@Scheduled(fixedDelay = 3_600_000)// Every hour
	public void expireOldJobs() {
		synchronized (codeSystemJobs) {
			// Delete jobs over 7 days old
			GregorianCalendar maxAge = new GregorianCalendar();
			maxAge.add(Calendar.DAY_OF_YEAR, -7);
			for (Map<String, AsyncJob> jobs : codeSystemJobs.values()) {
				List<String> expired = new ArrayList<>();
				for (Map.Entry<String, AsyncJob> entry : jobs.entrySet()) {
					if (entry.getValue().getCreated().before(maxAge.getTime())) {
						expired.add(entry.getKey());
					}
				}
				for (String key : expired) {
					jobs.remove(key);
				}
			}
		}
	}

}
