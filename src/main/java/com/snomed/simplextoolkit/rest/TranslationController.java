package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.ConceptMini;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.domain.CodeSystem;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.ChangeSummary;
import com.snomed.simplextoolkit.service.TranslationService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static com.snomed.simplextoolkit.rest.ControllerHelper.normaliseFilename;

@RestController
@Api(tags = "Translation", description = "-")
@RequestMapping("api/{codeSystem}/translations")
public class TranslationController {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@Autowired
	private TranslationService translationService;

	@GetMapping
	public List<ConceptMini> listTranslations(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return snowstormClient.getRefsets("<900000000000506000", snowstormClient.getCodeSystemOrThrow(codeSystem));
	}

	@GetMapping(path = "{refsetId}/spreadsheet", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public void downloadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ConceptMini refset = snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String filename = "Translation_" + normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		translationService.downloadTranslationAsSpreadsheet(refsetId, theCodeSystem, response.getOutputStream());
	}

	@PutMapping(path = "{refsetId}/spreadsheet", consumes = "multipart/form-data")
	public ChangeSummary uploadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam String languageCode, @RequestParam MultipartFile file) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return translationService.uploadTranslationAsCSV(refsetId, languageCode, theCodeSystem, file.getInputStream());
	}

}