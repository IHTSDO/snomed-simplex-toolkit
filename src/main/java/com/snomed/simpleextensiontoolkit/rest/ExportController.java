package com.snomed.simpleextensiontoolkit.rest;

import com.snomed.simpleextensiontoolkit.exceptions.ServiceException;
import com.snomed.simpleextensiontoolkit.service.CodeSystemConfigService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@Api(tags = "Export", description = "-")
@RequestMapping("api/rf2-export")
public class ExportController {

	@Autowired
	private CodeSystemConfigService configService;

	@GetMapping(value = "delta", produces="application/zip")
	public void getDelta(HttpServletResponse response) throws ServiceException, IOException {
		response.setHeader("Content-Disposition", "attachment; filename=\"" + getFilename("Delta") + "\"");
		configService.getSnowstormClient().exportRF2(response.getOutputStream(), "DELTA");
	}

	@GetMapping(value = "snapshot", produces="application/zip")
	public void getSnapshot(HttpServletResponse response) throws ServiceException, IOException {
		response.setHeader("Content-Disposition", "attachment; filename=\"" + getFilename("Snapshot") + "\"");
		configService.getSnowstormClient().exportRF2(response.getOutputStream(), "SNAPSHOT");
	}

	private String getFilename(String exportType) throws ServiceException {
		return String.format("%s-%s-Unpublished-" + exportType + ".zip", configService.getConfig().getCodesystem(), new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()));
	}

}
