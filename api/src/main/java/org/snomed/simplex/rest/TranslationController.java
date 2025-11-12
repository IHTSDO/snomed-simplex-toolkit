package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.*;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.external.WeblateLanguageInitialisationJobService;
import org.snomed.simplex.service.external.WeblateLanguageInitialisationRequest;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.service.job.JobType;
import org.snomed.simplex.weblate.TranslationLLMService;
import org.snomed.simplex.weblate.WeblateService;
import org.snomed.simplex.weblate.WeblateSetService;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.snomed.simplex.weblate.domain.WeblateUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Tag(name = "Translation", description = "-")
@RequestMapping("api")
public class TranslationController {

	public static final AsyncJob DUMMY_COMPLETE = new AsyncJob(null, null) {
		@Override
		public JobType getJobType() {
			return JobType.REFSET_CHANGE;
		}

		@Override
		public JobStatus getStatus() {
			return JobStatus.COMPLETE;
		}
	};
	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final ContentProcessingJobService jobService;
	private final ActivityService activityService;
	private final WeblateService weblateService;
	private final WeblateSetService weblateSetService;
	private final TranslationLLMService translationLLMService;

	public TranslationController(SnowstormClientFactory snowstormClientFactory, TranslationService translationService, ContentProcessingJobService jobService,
			ActivityService activityService, WeblateService weblateService, WeblateSetService weblateSetService, TranslationLLMService translationLLMService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.jobService = jobService;
		this.activityService = activityService;
		this.weblateService = weblateService;
		this.weblateSetService = weblateSetService;
		this.translationLLMService = translationLLMService;
	}

	@GetMapping("{codeSystem}/translations")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<ConceptMini> listTranslations(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return translationService.listTranslations(snowstormClient.getCodeSystemOrThrow(codeSystem), snowstormClientFactory.getClient());
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

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-setup")
	@Operation(summary = "Setup a language refset in Translation Tool.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob initialiseInWeblate(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		weblateService.getCreateWeblateUser();
		WeblateLanguageInitialisationRequest request = new WeblateLanguageInitialisationRequest(refsetId);
		WeblateLanguageInitialisationJobService weblateLanguageInitialisationJobService = weblateService.getWeblateLanguageInitialisationJobService();
		return activityService.startExternalServiceActivity(theCodeSystem, ComponentType.TRANSLATION, refsetId, ActivityType.WEBLATE_LANGUAGE_INITIALISATION, weblateLanguageInitialisationJobService, request);
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/weblate/users")
	@Operation(summary = "Get Weblate users for a specific refset translation team.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<WeblateUser> getWeblateUsersForRefset(@PathVariable String codeSystem, @PathVariable String refsetId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String languageCode = theCodeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset %s".formatted(refsetId), HttpStatus.CONFLICT);
		}
		weblateService.getCreateWeblateUser();
		return weblateService.getUsersForLanguage(languageCode, refsetId);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/assign-work")
	@Operation(summary = "Assign work to users for a specific translation set.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void assignWorkToUsers(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label, @RequestBody AssignWorkRequest request) throws ServiceException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		weblateService.getCreateWeblateUser();
		weblateSetService.assignWorkToUsers(codeSystem, refsetId, label, request);
	}

	@GetMapping("{codeSystem}/translations/weblate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<WeblateTranslationSet> listAllWeblateSets(@PathVariable String codeSystem) throws ServiceExceptionWithStatusCode {
		List<WeblateTranslationSet> translationSets = weblateSetService.findByCodeSystem(codeSystem);
		Set<String> languageCodeRefsetIds = translationSets.stream().map(WeblateTranslationSet::getLanguageCodeWithRefsetId).collect(Collectors.toSet());
		weblateService.runUserAccessCheck(languageCodeRefsetIds);
		return translationSets;
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/weblate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<WeblateTranslationSet> listWeblateSets(@PathVariable String codeSystem, @PathVariable String refsetId) {
		return weblateSetService.findByCodeSystemAndRefset(codeSystem, refsetId);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set")
	@Operation(summary = "Create a new translation set for a language refset.",
			description = "Creates a new translation set in Translation Tool. The 'label' parameter must be a lowercase URL-friendly string using characters [a-z0-9_-]. The 'name' parameter is the human-readable display name for the translation set.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public WeblateTranslationSet createWeblateSet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestBody CreateWeblateTranslationSet createRequest) throws ServiceException {

		String label = createRequest.getLabel();

		// Validate that label is all lowercase and URL compatible
		if (label == null || label.trim().isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Label parameter cannot be null or empty.", HttpStatus.BAD_REQUEST);
		}

		if (!label.equals(label.toLowerCase())) {
			throw new ServiceExceptionWithStatusCode("Label parameter must be all lowercase.", HttpStatus.BAD_REQUEST);
		}

		// Check for URL compatibility - only allow lowercase letters, numbers, hyphens, and underscores
		if (!label.matches("^[a-z0-9_-]+$")) {
			throw new ServiceExceptionWithStatusCode("Label parameter must contain only lowercase letters, numbers, hyphens, and underscores.", HttpStatus.BAD_REQUEST);
		}

		WeblateTranslationSet set = new WeblateTranslationSet(codeSystem, refsetId, createRequest.getName(), label,
			createRequest.getEcl(), createRequest.getSubsetType(), createRequest.getSelectionCodesystem());

		return weblateSetService.createSet(set);
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public WeblateTranslationSet getWeblateSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) throws ServiceExceptionWithStatusCode {
		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		int translated = weblateSetService.getStateCount(translationSet, "translated");
		translationSet.setTranslated(translated);

		// Get count of translations changed since the set was created or last pulled
		int changedSince = weblateSetService.getChangedSinceCount(translationSet);
		translationSet.setChangedSinceCreatedOrLastPulled(changedSince);

		return translationSet;
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/sample-rows")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public WeblatePage<WeblateUnit> getSampleWeblateContent(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
		@RequestParam(required = false, defaultValue = "10") int pageSize) throws ServiceExceptionWithStatusCode {

		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		WeblatePage<WeblateUnit> sampleRows = weblateSetService.getSampleRows(translationSet, pageSize);
		sampleRows.results().forEach(WeblateUnit::blankLabels);
		return sampleRows.withoutPagination();
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/sample-row/{conceptId}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public WeblateUnit getSampleWeblateContent(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
		@PathVariable String conceptId, @RequestParam(required = false, defaultValue = "10") int pageSize) throws ServiceExceptionWithStatusCode {

		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		WeblateUnit sampleRow = weblateSetService.getSampleRow(translationSet, conceptId);
		if (sampleRow != null) {
			sampleRow.blankLabels();
		}
		return sampleRow;
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/pull-content")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob pullWeblateContent(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) throws ServiceExceptionWithStatusCode {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		final ContentJob weblatePull = new ContentJob(theCodeSystem, "Translation Tool pull", refsetId);
		return jobService.queueContentJob(weblatePull, refsetId, activity,
			job -> weblateSetService.pullTranslationSubset(weblatePull, label));
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/ai-language-advice")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void saveAiLanguageAdvice(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
		@RequestBody AiLanguageAdviceRequest request) throws ServiceExceptionWithStatusCode {

		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		translationSet.setAiLanguageAdvice(request.languageAdvice());
		weblateSetService.updateSet(translationSet);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/ai-setup")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void translationSetAiSetup(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
		@RequestBody AiSetupRequest request) throws ServiceExceptionWithStatusCode {

		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		translationSet.setAiLanguageAdvice(request.languageAdvice());
		translationSet.setAiGoldenSet(request.aiGoldenSet());
		weblateSetService.updateSet(translationSet);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/ai-suggestion")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Map<String, List<String>> aiGoldenSetSuggestion(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
		@RequestBody List<String> englishTerm) throws ServiceExceptionWithStatusCode {

		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		return translationLLMService.suggestTranslations(translationSet, englishTerm, true, true);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/run-ai-batch")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void runAiBatchTranslate(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
		@RequestBody BatchTranslateRequest request) throws ServiceException {

		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		weblateSetService.runAiBatchTranslate(translationSet, request);
	}

	@DeleteMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}")
	@Operation(summary = "Asynchronously delete a translation set.",
			description = "Deletes a Translation Tool translation set as an asynchronous operation. The status of the set will be set to DELETING immediately. " +
				"The set will be deleted from the system over the next few minutes. Sets with a status of DELETING should be hidden from the user.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteWeblateSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) throws ServiceExceptionWithStatusCode {
		WeblateTranslationSet translationSet = weblateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		weblateSetService.deleteSet(translationSet);
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/weblate", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadTranslationFromWeblate(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			@RequestParam(defaultValue = "true") boolean translationTermsUseTitleCase) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Translation upload", refsetId)
			.addUpload(file.getInputStream(), file.getOriginalFilename());
		return jobService.queueContentJob(contentJob, refsetId, activity,
			asyncJob -> translationService.uploadTranslationAsWeblateCSV(translationTermsUseTitleCase, asyncJob));
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
	public String getWeblateMarkdown(@RequestParam Long conceptId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		return translationService.getWeblateMarkdown(conceptId, snowstormClient);
	}
}
