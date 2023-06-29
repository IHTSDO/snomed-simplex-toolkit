package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.domain.AsyncJob;
import com.snomed.simplextoolkit.service.JobService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(tags = "Processing Jobs", description = "-")
@RequestMapping("api/jobs")
public class JobController {

	@Autowired
	private JobService service;

	@GetMapping
	public List<AsyncJob> listJobs(@RequestParam(required = false) String refsetId) {
		return service.listJobs(refsetId);
	}

	@GetMapping("/{jobId}")
	public AsyncJob getJob(@PathVariable String jobId) {
		return service.getAsyncJob(jobId);
	}

}
