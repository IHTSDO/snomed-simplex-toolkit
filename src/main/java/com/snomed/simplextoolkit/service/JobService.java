package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.domain.AsyncJob;
import com.snomed.simplextoolkit.domain.JobStatus;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.apache.catalina.security.SecurityUtil;
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
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class JobService {

	private final Map<String, AsyncJob> cache;
	private final ExecutorService executorService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public JobService(@Value("${job.concurrent.threads}") int nThreads) {
		cache = new HashMap<>();
		executorService = Executors.newFixedThreadPool(nThreads);
	}

	public AsyncJob runJob(String display, InputStream jobInputStream, String refsetId, AsyncFunction function) throws IOException {
		AsyncJob asyncJob = new AsyncJob(display);
		asyncJob.setRefsetId(refsetId);

		File tempFile = File.createTempFile(asyncJob.getId(), "txt");
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			StreamUtils.copy(jobInputStream, out);
		}

		asyncJob.setTempFile(tempFile);
		asyncJob.setStatus(JobStatus.QUEUED);
		final SecurityContext userSecurityContext = SecurityContextHolder.getContext();
		executorService.submit(() -> {
			SecurityContextHolder.setContext(userSecurityContext);
			try {
				asyncJob.setStatus(JobStatus.IN_PROGRESS);
				ChangeSummary changeSummary = function.run(asyncJob);
				asyncJob.setChangeSummary(changeSummary);
				asyncJob.setStatus(JobStatus.COMPLETED);
			} catch (ServiceException e) {
				asyncJob.setStatus(JobStatus.FAILED);
				asyncJob.setServiceException(e);
			} finally {
				if (!tempFile.delete()) {
					logger.info("Failed to delete temp file {}", tempFile.getAbsoluteFile());
				}
			}
		});
		cache.put(asyncJob.getId(), asyncJob);
		return asyncJob;
	}

	public AsyncJob getAsyncJob(String jobId) {
		synchronized (cache) {
			return cache.get(jobId);
		}
	}

	public List<AsyncJob> listJobs(String refsetId) {
		List<AsyncJob> jobs = new ArrayList<>(cache.values());
		if (refsetId != null) {
			jobs = jobs.stream().filter(job -> refsetId.equals(job.getRefsetId())).collect(Collectors.toList());
		}
		jobs.sort(Comparator.comparing(AsyncJob::getCreated).reversed());
		return jobs;
	}

	@Scheduled(fixedDelay = 3_600_000)// Every hour
	public void expireOldJobs() {
		synchronized (cache) {
			GregorianCalendar maxAge = new GregorianCalendar();
			maxAge.add(Calendar.DAY_OF_YEAR, -7);
			List<String> expired = new ArrayList<>();
			for (Map.Entry<String, AsyncJob> entry : cache.entrySet()) {
				if (entry.getValue().getCreated().before(maxAge.getTime())) {
					expired.add(entry.getKey());
				}
			}
			for (String key : expired) {
				cache.remove(key);
			}
		}
	}
}
