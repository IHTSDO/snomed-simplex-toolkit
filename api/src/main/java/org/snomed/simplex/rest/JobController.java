package org.snomed.simplex.rest;

import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.JobType;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Processing Jobs", description = "-")
@RequestMapping("api/{codeSystem}/jobs")
public class JobController {

	@Autowired
	private JobService service;

	@GetMapping
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<AsyncJob> listJobs(@PathVariable String codeSystem, @RequestParam(required = false) String refsetId,
			@RequestParam(required = false) JobType jobType) {

		return service.listJobs(codeSystem, refsetId, jobType);
	}

	@GetMapping("/{jobId}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob getJob(@PathVariable String codeSystem, @PathVariable String jobId) {
		return service.getAsyncJob(codeSystem, jobId);
	}

}
