package com.snomed.simplextoolkit.rest;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.client.domain.ConceptMini;
import com.snomed.simplextoolkit.client.domain.Concepts;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.CreateConceptRequest;
import com.snomed.simplextoolkit.rest.pojos.LanguageCode;
import com.snomed.simplextoolkit.service.JobService;
import com.snomed.simplextoolkit.service.TranslationService;
import com.snomed.simplextoolkit.service.job.AsyncJob;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Translation", description = "-")
@RequestMapping("api")
public class TranslationController {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@Autowired
	private TranslationService translationService;

	@Autowired
	private JobService jobService;

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

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/weblate", consumes = "multipart/form-data")
	public AsyncJob uploadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam String languageCode, @RequestParam MultipartFile file,
			@RequestParam(defaultValue = "true") boolean translationTermsUseTitleCase,
			@RequestParam(defaultValue = "false") boolean overwriteExistingCaseSignificance,
			UriComponentsBuilder uriComponentBuilder) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		return jobService.queueContentJob(codeSystem, "Translation upload", file.getInputStream(), refsetId,
				asyncJob -> translationService.uploadTranslationAsWeblateCSV(refsetId, languageCode, theCodeSystem, asyncJob.getInputStream(),
				translationTermsUseTitleCase, snowstormClient, asyncJob));
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/refset-tool", consumes = "multipart/form-data")
	public AsyncJob uploadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			UriComponentsBuilder uriComponentBuilder) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		return jobService.queueContentJob(codeSystem, "Translation upload", file.getInputStream(), refsetId,
				asyncJob -> translationService.uploadTranslationAsRefsetToolArchive(refsetId, theCodeSystem, asyncJob.getInputStream(), snowstormClient, asyncJob));
	}

	@GetMapping(path = "language-codes")
	public List<LanguageCode> getLanguageCodes() {
		return translationService.getLanguageCodes();
	}

	static String getAsyncJobUrl(String id) {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
		return ServletUriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString()).path("/{id}").buildAndExpand(id).toUri().toString();
	}
}
