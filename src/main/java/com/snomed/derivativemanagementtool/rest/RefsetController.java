package com.snomed.derivativemanagementtool.rest;

import com.snomed.derivativemanagementtool.client.ConceptMini;
import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.domain.Concepts;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import com.snomed.derivativemanagementtool.service.CodeSystemConfigService;
import com.snomed.derivativemanagementtool.service.RefsetUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("refsets")
public class RefsetController {

	@Autowired
	private CodeSystemConfigService codeSystemConfigService;

	@Autowired
	private RefsetUpdateService refsetUpdateService;

	@GetMapping("simple")
	public List<ConceptMini> listSimpleRefsets() throws ServiceException {
		return getSnowstormClient().getRefsets("<" + Concepts.SIMPLE_TYPE_REFSET);
	}

	@GetMapping(path = "simple/{refsetId}/download-remote", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public void getSimpleRefsetSpreadsheet(@PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
		ConceptMini refset = codeSystemConfigService.getSnowstormClient().getRefset(refsetId);
		String filename = "SimpleRefset_";
		String name = "New";
		if (refset != null) {
			name = normaliseFilename(refset.getPt().getTerm());
		}
		filename += name + ".xlsx";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		refsetUpdateService.downloadSimpleRefset(refsetId, response.getOutputStream());
	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return codeSystemConfigService.getSnowstormClient();
	}

	private String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}

}
