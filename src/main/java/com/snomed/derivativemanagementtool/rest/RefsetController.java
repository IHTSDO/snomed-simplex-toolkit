package com.snomed.derivativemanagementtool.rest;

import com.snomed.derivativemanagementtool.client.ConceptMini;
import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.client.domain.Concept;
import com.snomed.derivativemanagementtool.domain.Concepts;
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

@RestController
@RequestMapping("api/refsets")
public class RefsetController {

	@Autowired
	private CodeSystemConfigService codeSystemConfigService;

	@Autowired
	private RefsetUpdateService refsetUpdateService;

	@GetMapping("simple")
	public List<ConceptMini> listSimpleRefsets() throws ServiceException {
		return getSnowstormClient().getRefsets("<" + Concepts.SIMPLE_TYPE_REFSET);
	}

	@PostMapping("simple")
	public ConceptMini createSimpleRefset(@RequestBody CreateConceptRequest createConceptRequest) throws ServiceException {
		SnowstormClient snowstormClient = getSnowstormClient();
		Concept concept = snowstormClient.createSimpleMetadataConcept(Concepts.SIMPLE_TYPE_REFSET, createConceptRequest.getPreferredTerm(), "foundation metadata concept");
		ConceptMini refset = snowstormClient.getRefset(concept.getConceptId());
		return refset;
	}

	@GetMapping(path = "simple/{refsetId}/spreadsheet", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public void downloadSimpleRefsetSpreadsheet(@PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
		ConceptMini refset = codeSystemConfigService.getSnowstormClient().getRefsetOrThrow(refsetId);
		String filename = "SimpleRefset_" + normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		refsetUpdateService.downloadSimpleRefsetAsSpreadsheet(refsetId, response.getOutputStream());
	}

	@PutMapping(path = "simple/{refsetId}/spreadsheet", consumes = "multipart/form-data")
	public ChangeSummary uploadSimpleRefsetSpreadsheet(@PathVariable String refsetId, @RequestParam MultipartFile file) throws ServiceException {
		try {
			return refsetUpdateService.updateSimpleRefsetViaSpreadsheet(refsetId, file.getInputStream());
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to open uploaded file.");
		}
	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return codeSystemConfigService.getSnowstormClient();
	}

	private String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}

}
