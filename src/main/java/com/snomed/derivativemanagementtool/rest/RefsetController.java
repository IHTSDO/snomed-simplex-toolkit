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

import java.util.List;

@RestController
@RequestMapping("refsets")
public class RefsetController {

	@Autowired
	private CodeSystemConfigService codeSystemConfigService;

	private RefsetUpdateService refsetUpdateService;

	@GetMapping("simple")
	public List<ConceptMini> listSimpleRefsets() throws ServiceException {
		return getSnowstormClient().getRefsets("<" + Concepts.SIMPLE_TYPE_REFSET);
	}

//	@GetMapping("simple/{refsetId}/spreadsheet")
//	public void getSimpleRefsetSpreadsheet(@PathVariable String refsetId) throws ServiceException {
//		refsetUpdateService.downloadRefset();
//	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return codeSystemConfigService.getSnowstormClient();
	}

}
