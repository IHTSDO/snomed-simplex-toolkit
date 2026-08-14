package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.JobType;
import org.snomed.simplex.snolate.service.TranslationStudioImportJobRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Content Processing Jobs", description = "-")
@RequestMapping("api")
public class ContentProcessingJobController {

	private final ContentProcessingJobService service;
	private final TranslationStudioImportJobRecordService translationStudioImportJobRecordService;

	public ContentProcessingJobController(ContentProcessingJobService service,
			TranslationStudioImportJobRecordService translationStudioImportJobRecordService) {
		this.service = service;
		this.translationStudioImportJobRecordService = translationStudioImportJobRecordService;
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

	@GetMapping(path = "/{codeSystem}/jobs/{jobId}/skipped-not-found.csv", produces = "text/csv")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void downloadSkippedNotFoundCsv(@PathVariable String codeSystem, @PathVariable String jobId,
			HttpServletResponse response) throws IOException {
		AsyncJob job = service.getAsyncJob(codeSystem, jobId);
		if (job == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found.");
		}
		response.setHeader("Content-Disposition", "attachment; filename=\"skipped-not-found-" + jobId + ".csv\"");
		try {
			translationStudioImportJobRecordService.writeSkippedNotFoundCsv(job, response.getOutputStream());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
		}
	}

}
