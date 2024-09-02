package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.RefsetMemberIntent;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.CreateConceptRequest;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.RefsetUpdateService;
import org.snomed.simplex.service.job.AsyncJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public abstract class AbstractRefsetController<T extends RefsetMemberIntent> {

	private final SnowstormClientFactory clientFactory;
	private final JobService jobService;


	protected AbstractRefsetController(SnowstormClientFactory clientFactory, JobService jobService) {
		this.clientFactory = clientFactory;
		this.jobService = jobService;
	}

	protected abstract String getRefsetType();

	protected abstract String getFilenamePrefix();

	protected abstract RefsetUpdateService<T> getRefsetService();

	@GetMapping
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<ConceptMini> listRefsets(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		return snowstormClient.getRefsets("<" + getRefsetType(), snowstormClient.getCodeSystemOrThrow(codeSystem));
	}

	@PostMapping
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public ConceptMini createRefsetConcept(@PathVariable String codeSystem, @RequestBody CreateConceptRequest createConceptRequest) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Concept concept = snowstormClient.createSimpleMetadataConcept(getRefsetType(), createConceptRequest.getPreferredTerm(), Concepts.FOUNDATION_METADATA_CONCEPT_TAG, theCodeSystem);
		return snowstormClient.getRefset(concept.getConceptId(), theCodeSystem);
	}

	@GetMapping(path = "{refsetId}/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void downloadRefsetSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ConceptMini refset = snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String filename = getFilenamePrefix() + "_" + ControllerHelper.normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		getRefsetService().downloadRefsetAsSpreadsheet(refsetId, response.getOutputStream(), theCodeSystem);
	}

	@PutMapping(path = "{refsetId}/spreadsheet", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadRefsetSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, @RequestParam MultipartFile file) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		try {
			InputStream inputStream = file.getInputStream();
			return jobService.queueContentJob(codeSystem, getSpreadsheetUploadJobName(), inputStream, refsetId,
					asyncJob -> getRefsetService().updateRefsetViaSpreadsheet(refsetId, inputStream, theCodeSystem));
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to open uploaded file.");
		}
	}

	protected abstract String getSpreadsheetUploadJobName();

	@DeleteMapping("{refsetId}")
	@Operation(summary = "Delete refset and all members.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteRefset(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		getRefsetService().deleteRefsetMembersAndConcept(refsetId, theCodeSystem);
	}

	protected SnowstormClient getSnowstormClient() throws ServiceException {
		return clientFactory.getClient();
	}

}
