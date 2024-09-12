package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
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
import java.util.function.Consumer;

@Service
public class JobService {

	private final Map<String, Map<String, AsyncJob>> codeSystemJobs;
	private final ExecutorService jobExecutorService;
	private final SupportRegister supportRegister;
	private final ActivityService activityService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public JobService(@Value("${job.concurrent.threads}") int nThreads, SupportRegister supportRegister, ActivityService activityService) {
		codeSystemJobs = new HashMap<>();
		jobExecutorService = Executors.newFixedThreadPool(nThreads);
		this.supportRegister = supportRegister;
		this.activityService = activityService;
	}

	public AsyncJob startExternalServiceJob(CodeSystem codeSystem, ActivityType activityType, Consumer<ExternalServiceJob> function) throws ServiceException {
		String shortName = codeSystem.getShortName();
		ExternalServiceJob asyncJob = new ExternalServiceJob(shortName, activityType.getDisplay(),
				codeSystem.getWorkingBranchPath(), codeSystem.getContentHeadTimestamp());

		asyncJob.setSecurityContext(SecurityContextHolder.getContext());
		asyncJob.setStatus(JobStatus.IN_PROGRESS);

		activityService.recordActivity(codeSystem.getShortName(), ComponentType.CODE_SYSTEM, activityType, () -> {
			function.accept(asyncJob);
			return null;
		});
		codeSystemJobs.computeIfAbsent(shortName, i -> new LinkedHashMap<>()).put(asyncJob.getId(), asyncJob);
		return asyncJob;
	}

	public AsyncJob queueContentJob(String codeSystem, String display, InputStream jobInputStream, String refsetId, Activity activity,
			AsyncFunction<ContentJob> function) throws IOException {

		ContentJob asyncJob = new ContentJob(codeSystem, display, refsetId);
		File tempFile = File.createTempFile(asyncJob.getId(), "txt");
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			StreamUtils.copy(jobInputStream, out);
		}
		asyncJob.setTempFile(tempFile);

		return doQueueJob(codeSystem, function, asyncJob, activity, () -> {
			if (!tempFile.delete()) {
				logger.info("Failed to delete temp file {}", tempFile.getAbsoluteFile());
			}
		});
	}

	private <T extends AsyncJob> T doQueueJob(String codeSystem, AsyncFunction<T> function, T asyncJob, Activity activity, Runnable onCompleteRunnable) {
		// Add job to thread limited executor service to be run when there is capacity
		final SecurityContext userSecurityContext = SecurityContextHolder.getContext();
		jobExecutorService.submit(() -> {
			SecurityContextHolder.setContext(userSecurityContext);
			try {
				asyncJob.setStatus(JobStatus.IN_PROGRESS);
				ChangeSummary changeSummary = function.run(asyncJob);
				asyncJob.setChangeSummary(changeSummary);
				asyncJob.setStatus(JobStatus.COMPLETE);
			} catch (ServiceException e) {
				asyncJob.setStatus(JobStatus.SYSTEM_ERROR);
				asyncJob.setServiceException(e);
				activity.exception(e);
			} catch (Exception e) {
				ServiceException serviceException = new ServiceException("Unexpected error.", e);
				activity.exception(serviceException);
				supportRegister.handleSystemError(asyncJob, "Unexpected error.", serviceException);
			} finally {
				activityService.manualSaveActivity(activity);
				if (onCompleteRunnable != null) {
					onCompleteRunnable.run();
				}
			}
		});

		asyncJob.setStatus(JobStatus.QUEUED);
		codeSystemJobs.computeIfAbsent(codeSystem, i -> new LinkedHashMap<>()).put(asyncJob.getId(), asyncJob);
		return asyncJob;
	}

	public AsyncJob getAsyncJob(String codeSystem, String jobId) {
		synchronized (codeSystemJobs) {
			return codeSystemJobs.getOrDefault(codeSystem, Collections.emptyMap()).get(jobId);
		}
	}

	public List<AsyncJob> listJobs(String codeSystem, String refsetId, JobType jobType) {
		List<AsyncJob> jobs = new ArrayList<>(codeSystemJobs.getOrDefault(codeSystem, Collections.emptyMap()).values());
		return jobs.stream()
				.filter(job -> jobType == null || job.getJobType() == jobType)
				.filter(job -> refsetId == null || (job instanceof ContentJob && refsetId.equals(((ContentJob)job).getRefsetId())))
				.sorted(Comparator.comparing(AsyncJob::getCreated).reversed())
				.toList();
	}

	@Scheduled(fixedDelay = 3_600_000)// Every hour
	public void expireOldJobs() {
		synchronized (codeSystemJobs) {
			GregorianCalendar maxAge = new GregorianCalendar();
			maxAge.add(Calendar.DAY_OF_YEAR, -7);
			for (Map<String, AsyncJob> codeSystemJobs : codeSystemJobs.values()) {
				List<String> expired = new ArrayList<>();
				for (Map.Entry<String, AsyncJob> entry : codeSystemJobs.entrySet()) {
					if (entry.getValue().getCreated().before(maxAge.getTime())) {
						expired.add(entry.getKey());
					}
				}
				for (String key : expired) {
					codeSystemJobs.remove(key);
				}
			}
		}
	}

	public AsyncJob getLatestJobOfType(String codeSystem, String display) {
		List<AsyncJob> list = codeSystemJobs.getOrDefault(codeSystem, Collections.emptyMap()).values().stream()
				.filter(job -> display.equals(job.getDisplay())).toList();
		return list.isEmpty() ? null : list.get(list.size() - 1);
	}
}
