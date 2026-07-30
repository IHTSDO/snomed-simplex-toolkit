package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.authoringservices.APTask;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CreateTranslationRequest;
import org.snomed.simplex.rest.pojos.LanguageCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

	public TranslationController(SnowstormClientFactory snowstormClientFactory, TranslationService translationService,
			ContentProcessingJobService jobService, ActivityService activityService) {

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

	@GetMapping("{codeSystem}/translations/current-ap-task")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public APTask getTranslationAPTask(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return translationService.getTranslationTask(theCodeSystem);
	}

	@PostMapping("{codeSystem}/translations")
	@PreAuthorize("hasPermission('ADMIN', #codeSystem)")// Admin needed because it changes branch metadata
	public void createTranslation(@PathVariable String codeSystem, @RequestBody CreateTranslationRequest request) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		activityService.runActivity(codeSystem, ComponentType.TRANSLATION, ActivityType.CREATE, () ->
			translationService.createTranslation(request.getPreferredTerm(), request.getLanguageCode(), snowstormClient.getCodeSystemOrThrow(codeSystem))
		);
	}

	@PostMapping("{codeSystem}/translations/us-english-synonyms/show")
	@Operation(summary = "Enable US English synonyms artifact for this edition.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void showUsEnglishSynonyms(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		translationService.showUsEnglishSynonyms(theCodeSystem);
	}

	@PostMapping("{codeSystem}/translations/gb-english-synonyms/show")
	@Operation(summary = "Enable GB English synonyms artifact for this edition.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void showGbEnglishSynonyms(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		translationService.showGbEnglishSynonyms(theCodeSystem);
	}

	@DeleteMapping("{codeSystem}/translations/{refsetId}")
	@Operation(summary = "Delete all language refset members and refset concept.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteRefset(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		rejectFoundationEnglishLangRefset(refsetId);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		activityService.runActivity(codeSystem, ComponentType.TRANSLATION, ActivityType.DELETE, refsetId, () -> {
			translationService.deleteTranslationAndMembers(refsetId, theCodeSystem);
			return null;
		});
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/translation-csv", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadTranslationFromCsv(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Translation upload", refsetId)
				.addUpload(file.getInputStream(), file.getOriginalFilename());
		return jobService.queueContentJob(contentJob, refsetId, activity, translationService::uploadTranslationCsv);
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/refset-tool", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadTranslationFromRefsetAndTranslationTool(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			@RequestParam(required = false, defaultValue = "true") boolean ignoreCaseInImport) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Translation upload", refsetId)
				.addUpload(file.getInputStream(), file.getOriginalFilename());
		return jobService.queueContentJob(contentJob, refsetId, activity,
				job -> translationService.uploadTranslationAsRefsetToolArchive(job, ignoreCaseInImport));
	}

	@GetMapping(path = "language-codes")
	public List<LanguageCode> getLanguageCodes() {
		return translationService.getLanguageCodes();
	}

	private static void rejectFoundationEnglishLangRefset(String refsetId) throws ServiceExceptionWithStatusCode {
		if (TranslationService.isFoundationEnglishLangRefset(refsetId)) {
			throw new ServiceExceptionWithStatusCode(
					"Foundation English synonym refsets cannot be modified through this operation.", HttpStatus.BAD_REQUEST);
		}
	}

}
