package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.service.job.*;
import com.snomed.simplextoolkit.domain.JobStatus;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Collectors;

@Service
public class JobService {

	private final Map<String, Map<String, AsyncJob>> codeSystemJobs;

	private final ExecutorService jobExecutorService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public JobService(@Value("${job.concurrent.threads}") int nThreads) {
		codeSystemJobs = new HashMap<>();
		jobExecutorService = Executors.newFixedThreadPool(nThreads);
	}

	public AsyncJob queueRefsetContentJob(String codeSystem, String display, InputStream jobInputStream, String refsetId, AsyncFunction<RefsetJob> function) throws IOException {
		RefsetJob asyncJob = new RefsetJob(codeSystem, display, refsetId);
		File tempFile = File.createTempFile(asyncJob.getId(), "txt");
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			StreamUtils.copy(jobInputStream, out);
		}
		asyncJob.setTempFile(tempFile);

		return doQueueJob(codeSystem, function, asyncJob, () -> {
			if (!tempFile.delete()) {
				logger.info("Failed to delete temp file {}", tempFile.getAbsoluteFile());
			}
		});
	}

	public AsyncJob startExternalServiceJob(CodeSystem codeSystem, String display, Consumer<ExternalServiceJob> function) {
		String shortName = codeSystem.getShortName();
		ExternalServiceJob asyncJob = new ExternalServiceJob(shortName, display);
		asyncJob.setSecurityContext(SecurityContextHolder.getContext());
		asyncJob.setBranch(codeSystem.getWorkingBranchPath());
		asyncJob.setStatus(JobStatus.IN_PROGRESS);

		function.accept(asyncJob);
		codeSystemJobs.computeIfAbsent(shortName, i -> new LinkedHashMap<>()).put(asyncJob.getId(), asyncJob);
		return asyncJob;
	}

	private AsyncJob doQueueJob(String codeSystem, AsyncFunction function, AsyncJob asyncJob, Runnable onCompleteRunnable) {
		// Add job to thread limited executor service to be run when there is capacity
		final SecurityContext userSecurityContext = SecurityContextHolder.getContext();
		jobExecutorService.submit(() -> {
			SecurityContextHolder.setContext(userSecurityContext);
			try {
				asyncJob.setStatus(JobStatus.IN_PROGRESS);
				ChangeSummary changeSummary = function.run(asyncJob);
				asyncJob.setChangeSummary(changeSummary);
				asyncJob.setStatus(JobStatus.COMPLETED);
			} catch (ServiceException e) {
				asyncJob.setStatus(JobStatus.ERROR);
				asyncJob.setServiceException(e);
			} finally {
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

	public List<AsyncJob> listJobs(String codeSystem, String refsetId) {
		List<AsyncJob> jobs = new ArrayList<>(codeSystemJobs.getOrDefault(codeSystem, Collections.emptyMap()).values());
		if (refsetId != null) {
			jobs = jobs.stream().filter(job -> job instanceof RefsetJob && refsetId.equals(((RefsetJob)job).getRefsetId())).collect(Collectors.toList());
		}
		jobs.sort(Comparator.comparing(AsyncJob::getCreated).reversed());
		return jobs;
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
}
