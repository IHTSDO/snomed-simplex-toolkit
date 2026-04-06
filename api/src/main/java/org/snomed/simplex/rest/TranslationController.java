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
import org.snomed.simplex.rest.pojos.*;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.snolate.service.SnolateTranslationToolService;
import org.snomed.simplex.snolate.sets.SnolateSetService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.translation.TranslationLLMService;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Translation", description = "-")
@RequestMapping("api")
public class TranslationController {

	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final ContentProcessingJobService jobService;
	private final ActivityService activityService;
	private final SnolateSetService snolateSetService;
	private final SnolateTranslationToolService snolateTranslationToolService;
	private final TranslationLLMService translationLLMService;

	public TranslationController(SnowstormClientFactory snowstormClientFactory, TranslationService translationService,
			ContentProcessingJobService jobService, ActivityService activityService, SnolateSetService snolateSetService,
			SnolateTranslationToolService snolateTranslationToolService, TranslationLLMService translationLLMService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.jobService = jobService;
		this.activityService = activityService;
		this.snolateSetService = snolateSetService;
		this.snolateTranslationToolService = snolateTranslationToolService;
		this.translationLLMService = translationLLMService;
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

	@DeleteMapping("{codeSystem}/translations/{refsetId}")
	@Operation(summary = "Delete all language refset members and refset concept.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteRefset(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		activityService.runActivity(codeSystem, ComponentType.TRANSLATION, ActivityType.DELETE, refsetId, () -> {
			translationService.deleteTranslationAndMembers(refsetId, theCodeSystem);
			return null;
		});
	}

	@GetMapping("{codeSystem}/translations/snolate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSet> listAllSnolateSets(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.getCodeSystemOrThrow(codeSystem);
		List<SnolateTranslationSet> sets = snolateSetService.findByCodeSystem(codeSystem);
		sets.forEach(snolateTranslationToolService::applyDashboardMetadata);
		snolateTranslationToolService.applyCounts(sets);
		return sets;
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/snolate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSet> listSnolateSets(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		List<SnolateTranslationSet> sets = snolateSetService.findByCodeSystemAndRefset(codeSystem, refsetId);
		sets.forEach(snolateTranslationToolService::applyDashboardMetadata);
		snolateTranslationToolService.applyCounts(sets);
		return sets;
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set")
	@Operation(summary = "Create a new Snolate translation set for a language refset.",
			description = "The 'label' parameter must be a lowercase URL-friendly string using characters [a-z0-9_-]. The 'name' parameter is the human-readable display name for the translation set.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet createSnolateSet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestBody CreateSnolateTranslationSet createRequest) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);

		String label = createRequest.getLabel();
		if (label == null || label.trim().isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Label parameter cannot be null or empty.", HttpStatus.BAD_REQUEST);
		}
		if (!label.equals(label.toLowerCase())) {
			throw new ServiceExceptionWithStatusCode("Label parameter must be all lowercase.", HttpStatus.BAD_REQUEST);
		}
		if (!label.matches("^[a-z0-9_-]+$")) {
			throw new ServiceExceptionWithStatusCode("Label parameter must contain only lowercase letters, numbers, hyphens, and underscores.", HttpStatus.BAD_REQUEST);
		}

		SnolateTranslationSet set = new SnolateTranslationSet(codeSystem, refsetId, createRequest.getName(), label,
				createRequest.getEcl(), createRequest.getSubsetType(), createRequest.getSelectionCodesystem());
		return snolateSetService.createSet(set);
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet getSnolateSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label)
			throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateTranslationToolService.applyCounts(translationSet);
		snolateTranslationToolService.applyDashboardMetadata(translationSet);
		return translationSet;
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/rows")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public TranslationUnitPage<TranslationUnitRow> getSnolateSetRows(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label, @RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "25") int size) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		int safePage = Math.max(0, page);
		int safeSize = Math.min(2000, Math.max(1, size));
		TranslationUnitPage<TranslationUnitRow> result = snolateTranslationToolService.getRows(translationSet, safePage, safeSize);
		result.results().forEach(TranslationUnitRow::blankLabels);
		return result;
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/sample-row/{conceptId}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public TranslationUnitRow getSampleSnolateContent(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@PathVariable String conceptId) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.getCodeSystemOrThrow(codeSystem);
		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		TranslationUnitRow row = snolateTranslationToolService.getSampleRow(translationSet, conceptId, snowstormClient);
		if (row != null) {
			row.blankLabels();
		}
		return row;
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate/sync-from-snowstorm")
	@Operation(summary = "Synchronise language refset terms from Snowstorm into Snolate.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob synchroniseTranslationFromSnowstormToSnolate(@PathVariable String codeSystem, @PathVariable String refsetId)
			throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String languageCode = theCodeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset %s".formatted(refsetId), HttpStatus.CONFLICT);
		}

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Snolate sync from Snowstorm", refsetId);
		return jobService.queueContentJob(contentJob, refsetId, activity, job -> {
			translationService.synchroniseWholeTranslationFromSnowstormToSnolate(theCodeSystem, snowstormClient, languageCode, refsetId);
			return new ChangeSummary();
		});
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-setup")
	@Operation(summary = "Link a language refset to Snolate: sync from Snowstorm then record branch metadata.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob setupSnolateTranslation(@PathVariable String codeSystem, @PathVariable String refsetId)
			throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String languageCode = theCodeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset %s".formatted(refsetId), HttpStatus.CONFLICT);
		}

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.SNOLATE_LANGUAGE_INITIALISATION);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Translation Studio translation setup", refsetId);
		return jobService.queueContentJob(contentJob, refsetId, activity, job -> {
			translationService.synchroniseWholeTranslationFromSnowstormToSnolate(theCodeSystem, snowstormClient, languageCode, refsetId);
			snowstormClient.addSnolateTranslationLanguage(refsetId, languageCode, theCodeSystem);
			return new ChangeSummary();
		});
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/pull-content")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob pullSnolateContent(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody(required = false) APTaskRequest apTaskRequest) throws ServiceExceptionWithStatusCode {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		final ContentJob pullJob = new ContentJob(theCodeSystem, "Snolate subset pull", refsetId);
		if (apTaskRequest == null) {
			apTaskRequest = new APTaskRequest();
		}
		String assigneeUsername = apTaskRequest.getAssigneeUsername();
		String taskTitle = apTaskRequest.getTaskTitle();
		pullJob.setTaskCreationCallable(() -> translationService.getCreateTranslationTask(theCodeSystem, assigneeUsername, taskTitle));

		return jobService.queueContentJob(pullJob, refsetId, activity, job -> {
			SnolateTranslationSet set = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
			translationService.synchroniseSnolateSubsetToSnowstorm(theCodeSystem, snowstormClient, set);
			set.setLastPulled(new Date());
			snolateSetService.updateSet(set);
			return new ChangeSummary();
		});
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/ai-language-advice")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void saveAiLanguageAdvice(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody AiLanguageAdviceRequest request) throws ServiceExceptionWithStatusCode {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		translationSet.setAiLanguageAdvice(request.languageAdvice());
		snolateSetService.updateSet(translationSet);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/ai-setup")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void translationSetAiSetup(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody AiSetupRequest request) throws ServiceExceptionWithStatusCode {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		translationSet.setAiLanguageAdvice(request.languageAdvice());
		translationSet.setAiGoldenSet(request.aiGoldenSet());
		snolateSetService.updateSet(translationSet);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/ai-suggestion")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Map<String, List<String>> aiGoldenSetSuggestion(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody List<String> englishTerm) throws ServiceExceptionWithStatusCode {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		return translationLLMService.suggestTranslations(translationSet, englishTerm, true, true);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/run-ai-batch")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void runAiBatchTranslate(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody BatchTranslateRequest request) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateSetService.runAiBatchTranslate(translationSet, request);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}/refresh")
	@Operation(summary = "Refresh a translation set by re-running its ECL selection.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet refreshSnolateSet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		return snolateSetService.refreshSet(translationSet);
	}

	@DeleteMapping("{codeSystem}/translations/{refsetId}/snolate-set/{label}")
	@Operation(summary = "Asynchronously delete a Snolate translation set.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteSnolateSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) throws ServiceException {
		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateSetService.deleteSet(translationSet);
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

	@GetMapping(path = "translation-markdown", produces = "text/plain")
	public String getTranslationMarkdown(@RequestParam Long conceptId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return translationService.getConceptExplanationMarkdown(conceptId, snowstormClient);
	}
}
