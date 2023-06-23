package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.client.domain.ConceptMini;
import com.snomed.simplextoolkit.client.domain.Concepts;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.CreateConceptRequest;
import com.snomed.simplextoolkit.rest.pojos.LanguageCode;
import com.snomed.simplextoolkit.service.ChangeSummary;
import com.snomed.simplextoolkit.service.TranslationService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@Api(tags = "Translation", description = "-")
@RequestMapping("api")
public class TranslationController {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@Autowired
	private TranslationService translationService;

	@GetMapping("{codeSystem}/translations")
	public List<ConceptMini> listTranslations(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return translationService.listTranslations(snowstormClient.getCodeSystemOrThrow(codeSystem), snowstormClientFactory.getClient());
	}

	@PostMapping("{codeSystem}/translations")
	public void createTranslation(@PathVariable String codeSystem, @RequestBody CreateConceptRequest request) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.createSimpleMetadataConcept(Concepts.LANG_REFSET, request.getPreferredTerm(), Concepts.FOUNDATION_METADATA_CONCEPT_TAG,
				snowstormClient.getCodeSystemOrThrow(codeSystem));
	}

//	@GetMapping(path = "{codeSystem}/translations/{refsetId}/spreadsheet", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
//	public void downloadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
//		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
//		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
//		ConceptMini refset = snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
//		String filename = "Translation_" + normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
//		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
//		translationService.downloadTranslationAsSpreadsheet(refsetId, theCodeSystem, response.getOutputStream());
//	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/spreadsheet", consumes = "multipart/form-data")
	public ChangeSummary uploadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam String languageCode, @RequestParam MultipartFile file,
			@RequestParam(defaultValue = "true") boolean translationTermsUseTitleCase,
			@RequestParam(defaultValue = "false") boolean overwriteExistingCaseSignificance) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		translationService.uploadTranslationAsCSV(refsetId, languageCode, theCodeSystem, file.getInputStream(),
				overwriteExistingCaseSignificance, translationTermsUseTitleCase, snowstormClient);
		return new ChangeSummary(0, 0, 0, 0);
	}

	@GetMapping(path = "language-codes")
	public List<LanguageCode> getLanguageCodes() {
		return translationService.getLanguageCodes();
	}

}
