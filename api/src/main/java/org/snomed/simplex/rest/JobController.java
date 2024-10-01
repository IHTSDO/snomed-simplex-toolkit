package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.JobType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Processing Jobs", description = "-")
@RequestMapping("api")
public class JobController {

	private final JobService service;

	public JobController(JobService service) {
		this.service = service;
	}

	@Operation(summary = "Admin function to list system wide jobs")
	@GetMapping("/jobs")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public List<AsyncJob> listAllJobs(@RequestParam(required = false) String refsetId,
			@RequestParam(required = false) JobType jobType) {

		return service.listJobs(null, refsetId, jobType);
	}

	@GetMapping("/{codeSystem}/jobs")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<AsyncJob> listJobs(@PathVariable String codeSystem, @RequestParam(required = false) String refsetId,
			@RequestParam(required = false) JobType jobType) {

		return service.listJobs(codeSystem, refsetId, jobType);
	}

	@GetMapping("/{codeSystem}/jobs/{jobId}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob getJob(@PathVariable String codeSystem, @PathVariable String jobId) {
		return service.getAsyncJob(codeSystem, jobId);
	}

}
