package com.snomed.simpleextensiontoolkit.rest;

import com.snomed.simpleextensiontoolkit.client.ConceptMini;
import com.snomed.simpleextensiontoolkit.client.SnowstormClient;
import com.snomed.simpleextensiontoolkit.client.SnowstormClientFactory;
import com.snomed.simpleextensiontoolkit.client.domain.Concept;
import com.snomed.simpleextensiontoolkit.domain.CodeSystem;
import com.snomed.simpleextensiontoolkit.exceptions.ServiceException;
import com.snomed.simpleextensiontoolkit.rest.pojos.CreateConceptRequest;
import com.snomed.simpleextensiontoolkit.service.ChangeSummary;
import com.snomed.simpleextensiontoolkit.service.RefsetUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public abstract class AbstractRefsetController {

	@Autowired
	private SnowstormClientFactory clientFactory;

	protected abstract String getRefsetType();

	protected abstract String getFilenamePrefix();

	protected abstract RefsetUpdateService getRefsetService();

	@GetMapping
	public List<ConceptMini> listRefsets(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		return snowstormClient.getRefsets("<" + getRefsetType(), snowstormClient.getCodeSystemOrThrow(codeSystem));
	}

	@PostMapping
	public ConceptMini createRefsetConcept(@PathVariable String codeSystem, @RequestBody CreateConceptRequest createConceptRequest) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Concept concept = snowstormClient.createSimpleMetadataConcept(getRefsetType(), createConceptRequest.getPreferredTerm(), "foundation metadata concept", theCodeSystem);
		return snowstormClient.getRefset(concept.getConceptId(), theCodeSystem);
	}

	@GetMapping(path = "{refsetId}/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public void downloadRefsetSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ConceptMini refset = snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String filename = getFilenamePrefix() + "_" + normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		getRefsetService().downloadRefsetAsSpreadsheet(refsetId, response.getOutputStream(), theCodeSystem);
	}

	@PutMapping(path = "{refsetId}/spreadsheet", consumes = "multipart/form-data")
	public ChangeSummary uploadRefsetSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, @RequestParam MultipartFile file) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		try {
			return getRefsetService().updateRefsetViaSpreadsheet(refsetId, file.getInputStream(), theCodeSystem);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to open uploaded file.");
		}
	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return clientFactory.getClient();
	}

	protected String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}

}
