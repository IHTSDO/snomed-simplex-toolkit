package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.CreateConceptRequest;
import org.snomed.simplex.rest.pojos.LanguageCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.job.AsyncJob;
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

	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final ContentProcessingJobService jobService;
	private final ActivityService activityService;

	public TranslationController(SnowstormClientFactory snowstormClientFactory, TranslationService translationService, ContentProcessingJobService jobService,
			ActivityService activityService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.jobService = jobService;
		this.activityService = activityService;
	}

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
		activityService.runActivity(codeSystem, ComponentType.TRANSLATION, ActivityType.CREATE, () ->
			snowstormClient.createSimpleMetadataConcept(Concepts.LANG_REFSET, request.getPreferredTerm(), Concepts.FOUNDATION_METADATA_CONCEPT_TAG,
					snowstormClient.getCodeSystemOrThrow(codeSystem))
		);
	}

	@DeleteMapping("{codeSystem}/translations/{refsetId}")
	@Operation(summary = "Delete all language refset members and refset concept.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteRefset(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		activityService.runActivity(codeSystem, ComponentType.TRANSLATION, ActivityType.DELETE, refsetId, () -> {
			translationService.deleteRefsetMembersAndConcept(refsetId, theCodeSystem);
			return null;
		});
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
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		return jobService.queueContentJob(theCodeSystem, "Translation upload", file.getInputStream(), file.getOriginalFilename(), refsetId,
				activity, asyncJob -> translationService.uploadTranslationAsWeblateCSV(languageCode, translationTermsUseTitleCase, asyncJob));
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/refset-tool", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadTranslationSpreadsheet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			UriComponentsBuilder uriComponentBuilder) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		return jobService.queueContentJob(theCodeSystem, "Translation upload", file.getInputStream(), file.getOriginalFilename(), refsetId,
				activity, translationService::uploadTranslationAsRefsetToolArchive);
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

	@GetMapping(path = "translation-markdown", produces = "text/plain")
	public String getWeblateMarkdown(@RequestParam Long conceptId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return translationService.getWeblateMarkdown(conceptId, snowstormClient);
	}
}
