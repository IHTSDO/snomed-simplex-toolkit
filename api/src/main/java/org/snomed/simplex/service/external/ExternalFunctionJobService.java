package org.snomed.simplex.service.external;

import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class ExternalFunctionJobService<T> {

	private final Map<String, LinkedHashMap<String, ExternalServiceJob>> codeSystemJobs;
	private final Map<String, ExternalServiceJob> jobsToMonitor = new HashMap<>();

	protected final SupportRegister supportRegister;
	protected final ActivityService activityService;

	protected ExternalFunctionJobService(SupportRegister supportRegister, ActivityService activityService) {
		this.supportRegister = supportRegister;
		this.activityService = activityService;
		codeSystemJobs = new HashMap<>();
	}

	public ExternalServiceJob callService(CodeSystem codeSystem, Activity activity, T requestParam) throws ServiceException {
		String shortName = activity.getCodesystem();

		ExternalServiceJob asyncJob = new ExternalServiceJob(codeSystem, activity.getActivityType().getDisplay(),
				codeSystem.getWorkingBranchPath(), codeSystem.getContentHeadTimestamp());
		asyncJob.setSecurityContext(SecurityContextHolder.getContext());
		asyncJob.setStatus(JobStatus.IN_PROGRESS);
		asyncJob.setActivity(activity);

		try {
			String externalJobId = doCallService(codeSystem, asyncJob, requestParam);
			if (externalJobId != null) {
				codeSystemJobs.computeIfAbsent(shortName, i -> new LinkedHashMap<>()).put(externalJobId, asyncJob);
				jobsToMonitor.put(externalJobId, asyncJob);
			}
		} catch (ServiceException e) {
			supportRegister.handleSystemError(asyncJob, "Failed to create " + getFunctionName() + ".", e);
			throw e;
		}
		return asyncJob;
	}

	public ExternalServiceJob getLatestJob(String codeSystem) {
		List<ExternalServiceJob> list = codeSystemJobs.getOrDefault(codeSystem, new LinkedHashMap<>()).values().stream().toList();
		return list.isEmpty() ? null : list.get(list.size() - 1);
	}

	@Scheduled(initialDelay = 15, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void monitorProgress() {
		Set<String> completedJobs = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : jobsToMonitor.entrySet()) {
			ExternalServiceJob job = entry.getValue();
			if (doMonitorProgress(job, entry.getKey())) {
				completedJobs.add(entry.getKey());
				Activity activity = job.getActivity();
				activityService.endAsynchronousActivity(activity);
			}
		}
		for (String completedJob : completedJobs) {
			jobsToMonitor.remove(completedJob);
		}
	}

	protected abstract String getFunctionName();

	protected abstract String doCallService(CodeSystem codeSystem, ExternalServiceJob job, T requestParam) throws ServiceException;

	protected abstract boolean doMonitorProgress(ExternalServiceJob value, String externalId);

	public boolean isJobBeingMonitored(String jobId) {
		return jobsToMonitor.containsKey(jobId);
	}

}
