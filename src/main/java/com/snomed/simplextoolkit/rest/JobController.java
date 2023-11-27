package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.service.JobService;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Processing Jobs", description = "-")
@RequestMapping("api/{codeSystem}/jobs")
public class JobController {

	@Autowired
	private JobService service;

	@GetMapping
	public List<AsyncJob> listJobs(@PathVariable String codeSystem, @RequestParam(required = false) String refsetId) {
		return service.listJobs(codeSystem, refsetId);
	}

	@GetMapping("/{jobId}")
	public AsyncJob getJob(@PathVariable String codeSystem, @PathVariable String jobId) {
		return service.getAsyncJob(codeSystem, jobId);
	}

}
