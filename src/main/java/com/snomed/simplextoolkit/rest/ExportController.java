package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@Api(tags = "Export", description = "-")
@RequestMapping("api/{codeSystem}/rf2-export")
public class ExportController {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@GetMapping(value = "delta", produces="application/zip")
	public void getDelta(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		doExport("Delta", "DELTA", codeSystem, response);
	}

	@GetMapping(value = "snapshot", produces="application/zip")
	public void getSnapshot(@PathVariable String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		doExport("Snapshot", "SNAPSHOT", codeSystem, response);
	}

	private void doExport(String filenameType, String exportType, String codeSystem, HttpServletResponse response) throws ServiceException, IOException {
		response.setHeader("Content-Disposition", "attachment; filename=\"" + getFilename(codeSystem, filenameType) + "\"");
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.exportRF2(response.getOutputStream(), exportType, theCodeSystem);
	}

	private String getFilename(String codeSystem, String exportType) {
		return String.format("%s-%s-Unpublished-" + exportType + ".zip", codeSystem, new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()));
	}

}
