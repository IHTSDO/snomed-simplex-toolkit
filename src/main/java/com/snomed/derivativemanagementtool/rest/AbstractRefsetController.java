package com.snomed.derivativemanagementtool.rest;

import com.snomed.derivativemanagementtool.client.ConceptMini;
import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.client.domain.Concept;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import com.snomed.derivativemanagementtool.rest.pojos.CreateConceptRequest;
import com.snomed.derivativemanagementtool.service.ChangeSummary;
import com.snomed.derivativemanagementtool.service.CodeSystemConfigService;
import com.snomed.derivativemanagementtool.service.RefsetUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public abstract class AbstractRefsetController {

	@Autowired
	protected CodeSystemConfigService codeSystemConfigService;

	protected abstract String getRefsetType();

	protected abstract String getFilenamePrefix();

	protected abstract RefsetUpdateService getRefsetService();

	@GetMapping
	public List<ConceptMini> listRefsets() throws ServiceException {
		return getSnowstormClient().getRefsets("<" + getRefsetType());
	}

	@PostMapping
	public ConceptMini createRefsetConcept(@RequestBody CreateConceptRequest createConceptRequest) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		Concept concept = snowstormClient.createSimpleMetadataConcept(getRefsetType(), createConceptRequest.getPreferredTerm(), "foundation metadata concept");
		return snowstormClient.getRefset(concept.getConceptId());
	}

	@GetMapping(path = "{refsetId}/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public void downloadRefsetSpreadsheet(@PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
		ConceptMini refset = codeSystemConfigService.getSnowstormClient().getRefsetOrThrow(refsetId);
		String filename = getFilenamePrefix() + "_" + normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		getRefsetService().downloadRefsetAsSpreadsheet(refsetId, response.getOutputStream());
	}

	@PutMapping(path = "{refsetId}/spreadsheet", consumes = "multipart/form-data")
	public ChangeSummary uploadRefsetSpreadsheet(@PathVariable String refsetId, @RequestParam MultipartFile file) throws ServiceException {
		try {
			return getRefsetService().updateRefsetViaSpreadsheet(refsetId, file.getInputStream());
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to open uploaded file.");
		}
	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return codeSystemConfigService.getSnowstormClient();
	}

	protected String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}

}
