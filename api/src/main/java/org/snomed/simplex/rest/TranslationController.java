package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.CreateConceptRequest;
import org.snomed.simplex.rest.pojos.LanguageCode;
import org.snomed.simplex.service.JobService;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.job.AsyncJob;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<ConceptMini> listTranslations(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return translationService.listTranslations(snowstormClient.getCodeSystemOrThrow(codeSystem), snowstormClientFactory.getClient());
	}

	@PostMapping("{codeSystem}/translations")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void createTranslation(@PathVariable String codeSystem, @RequestBody CreateConceptRequest request) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.createSimpleMetadataConcept(Concepts.LANG_REFSET, request.getPreferredTerm(), Concepts.FOUNDATION_METADATA_CONCEPT_TAG,
				snowstormClient.getCodeSystemOrThrow(codeSystem));
	}

	@DeleteMapping("{codeSystem}/translations/{refsetId}")
	@Operation(summary = "Delete all language refset members and refset concept.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteRefset(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		translationService.deleteRefsetMembersAndConcept(refsetId, theCodeSystem);
	}

//	@GetMapping(path = "{codeSystem}/translations/{refsetId}/spreadsheet", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
//  @PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
//	public void downloadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId, HttpServletResponse response) throws ServiceException, IOException {
//		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
//		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
//		ConceptMini refset = snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
//		String filename = "Translation_" + normaliseFilename(refset.getPt().getTerm()) + ".xlsx";
//		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
//		translationService.downloadTranslationAsSpreadsheet(refsetId, theCodeSystem, response.getOutputStream());
//	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/weblate", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
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
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
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
