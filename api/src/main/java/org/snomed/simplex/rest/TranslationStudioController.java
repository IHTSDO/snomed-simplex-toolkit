package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.*;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.service.job.TranslationStudioContentJob;
import org.snomed.simplex.snolate.domain.LanguagePolicyQuestionnaire;
import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationStatusLabels;
import org.snomed.simplex.snolate.service.LanguagePolicyQuestionnaireService;
import org.snomed.simplex.snolate.service.LanguageTranslationPolicyService;
import org.snomed.simplex.snolate.service.OutsideSetBehavior;
import org.snomed.simplex.snolate.service.SnolateTranslationService;
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

import static org.snomed.simplex.rest.TranslationStudioRequestSupport.*;

@RestController
@Tag(name = "Translation Studio", description = "-")
@RequestMapping("api/{codeSystem}/translation-studio")
public class TranslationStudioController {

	private final SnowstormClientFactory snowstormClientFactory;
	private final TranslationService translationService;
	private final ContentProcessingJobService jobService;
	private final SnolateSetService snolateSetService;
	private final SnolateTranslationService snolateTranslationService;
	private final TranslationLLMService translationLLMService;
	private final LanguageTranslationPolicyService languageTranslationPolicyService;
	private final LanguagePolicyQuestionnaireService languagePolicyQuestionnaireService;

	public TranslationStudioController(SnowstormClientFactory snowstormClientFactory, TranslationService translationService,
			ContentProcessingJobService jobService, SnolateSetService snolateSetService,
			SnolateTranslationService snolateTranslationService, TranslationLLMService translationLLMService,
			LanguageTranslationPolicyService languageTranslationPolicyService,
			LanguagePolicyQuestionnaireService languagePolicyQuestionnaireService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.jobService = jobService;
		this.snolateSetService = snolateSetService;
		this.snolateTranslationService = snolateTranslationService;
		this.translationLLMService = translationLLMService;
		this.languageTranslationPolicyService = languageTranslationPolicyService;
		this.languagePolicyQuestionnaireService = languagePolicyQuestionnaireService;
	}

	@GetMapping("sets/status")
	@Operation(summary = "Lightweight status snapshot for all translation sets (for dashboard polling).")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSetStatus> listSetStatuses(@PathVariable String codeSystem) {
		return snolateSetService.findByCodeSystem(codeSystem).stream()
				.map(SnolateTranslationSetStatus::from)
				.toList();
	}

	@GetMapping("sets")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSet> listAllSets(@PathVariable String codeSystem) {
		List<SnolateTranslationSet> sets = snolateSetService.findByCodeSystem(codeSystem);
		sets.forEach(snolateTranslationService::applyDashboardMetadata);
		snolateTranslationService.applyCounts(sets);
		return sets;
	}

	@PostMapping("sets/refresh-after-upgrade")
	@Operation(summary = "Queue refresh for all translation sets after a CodeSystem International Edition upgrade.",
			description = "Use when the edition upgrade was performed outside Simplex. Sets are queued for refresh using upgrade-specific statuses.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public RefreshTranslationSetsAfterUpgradeResponse refreshSetsAfterUpgrade(@PathVariable String codeSystem) throws ServiceException {
		return snolateSetService.refreshAllSetsAfterUpgrade(codeSystem);
	}

	@GetMapping("{refsetId}/sets")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSet> listSets(@PathVariable String codeSystem, @PathVariable String refsetId) {
		List<SnolateTranslationSet> sets = snolateSetService.findByCodeSystemAndRefset(codeSystem, refsetId);
		sets.forEach(snolateTranslationService::applyDashboardMetadata);
		snolateTranslationService.applyCounts(sets);
		return sets;
	}

	@PostMapping("{refsetId}/sets")
	@Operation(summary = "Create a new translation set for a language refset.",
			description = "The 'label' parameter must be a lowercase URL-friendly string using characters [a-z0-9_-]. The 'name' parameter is the human-readable display name for the translation set.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet createSet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestBody CreateSnolateTranslationSet createRequest) throws ServiceException {

		rejectFoundationEnglishLangRefset(refsetId);
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
		validateDescriptionLength(createRequest.getDescription());

		SnolateTranslationSet set = new SnolateTranslationSet(codeSystem, refsetId, createRequest.getName(), label,
				createRequest.getEcl(), createRequest.getSubsetType(), createRequest.getSelectionCodesystem());
		set.setDescription(normaliseDescription(createRequest.getDescription()));
		return snolateSetService.createSet(set);
	}

	@PutMapping("{refsetId}/sets/{label}")
	@Operation(summary = "Update a translation set display name and description.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet updateSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody UpdateSnolateTranslationSet updateRequest) throws ServiceException {

		ServiceHelper.requiredParameter("name", updateRequest.getName());
		if (updateRequest.getName().isBlank()) {
			throw new ServiceExceptionWithStatusCode("Parameter 'name' cannot be blank.", HttpStatus.BAD_REQUEST);
		}
		validateDescriptionLength(updateRequest.getDescription());

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		translationSet.setName(updateRequest.getName().trim());
		translationSet.setDescription(normaliseDescription(updateRequest.getDescription()));
		snolateSetService.updateSet(translationSet);
		snolateTranslationService.applyCounts(translationSet);
		snolateTranslationService.applyDashboardMetadata(translationSet);
		return translationSet;
	}

	@GetMapping("{refsetId}/sets/{label}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet getSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) throws ServiceException {
		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateTranslationService.applyCounts(translationSet);
		snolateTranslationService.applyDashboardMetadata(translationSet);
		return translationSet;
	}

	@GetMapping("{refsetId}/sets/{label}/rows")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public TranslationUnitPage<TranslationUnitRow> getSetRows(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label, @RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "25") int size,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String english,
			@RequestParam(required = false) String target) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		int safePage = Math.max(0, page);
		int safeSize = Math.min(2000, Math.max(1, size));
		TranslationStatus statusFilter = parseOptionalTranslationStatus(status);
		TranslationUnitPage<TranslationUnitRow> result = snolateTranslationService.getRows(translationSet, safePage, safeSize,
				statusFilter, english, target);
		result.results().forEach(TranslationUnitRow::blankLabels);
		return result;
	}

	@GetMapping(path = "{refsetId}/sets/{label}/csv", produces = "text/csv")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void downloadSetCsv(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label, @RequestParam(required = false) String status, HttpServletResponse response)
			throws ServiceException, IOException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		if (!translationSet.getStatus().isEditable()) {
			throw new ServiceExceptionWithStatusCode(
					"Translation set CSV export is only available when set status is READY.",
					HttpStatus.BAD_REQUEST);
		}
		TranslationStatus statusFilter = parseOptionalTranslationStatus(status);

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		ConceptMini refset = snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String languageDisplayName = SnolateTranslationService.displayLanguageDialect(refset.getPt().getTerm());

		String filename = ControllerHelper.normaliseFilename(translationSet.getName()) + "-"
				+ TranslationStatusLabels.exportFilenameSlug(statusFilter) + ".csv";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		snolateTranslationService.writeTranslationSetCsv(translationSet, statusFilter, languageDisplayName,
				response.getOutputStream());
	}

	@PutMapping(path = "{refsetId}/sets/{label}/csv", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadSetCsv(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label, @RequestParam MultipartFile file,
			@RequestParam String conceptColumn, @RequestParam List<String> termColumns, @RequestParam String status,
			@RequestParam(required = false, defaultValue = "SKIP") String outsideSetBehavior)
			throws ServiceException, IOException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		if (!translationSet.getStatus().isEditable()) {
			throw new ServiceExceptionWithStatusCode(
					"Translation set file import is only available when set status is READY.",
					HttpStatus.BAD_REQUEST);
		}
		TranslationStatus importStatus = parseImportTranslationStatus(status);
		OutsideSetBehavior outsideSet = parseOutsideSetBehavior(outsideSetBehavior);
		List<String> termColumnList = getTermColumnList(termColumns);

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION_STUDIO, ActivityType.UPDATE);
		ContentJob contentJob = new TranslationStudioContentJob(theCodeSystem,
				"Translation Studio set file import", refsetId)
				.addUpload(file.getInputStream(), file.getOriginalFilename());
		return jobService.queueContentJob(contentJob, refsetId, activity,
				job -> snolateTranslationService.importTranslationSetFile(
						translationSet, job.getInputStream(), job.getInputFileOriginalName(), conceptColumn,
						termColumnList, importStatus, outsideSet));
	}

	@PutMapping(path = "{refsetId}/csv", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadLanguageCsv(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file, @RequestParam String conceptColumn,
			@RequestParam List<String> termColumns, @RequestParam String status)
			throws ServiceException, IOException {

		TranslationStatus importStatus = parseImportTranslationStatus(status);
		List<String> termColumnList = getTermColumnList(termColumns);

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		String languageCode = resolveSnolateLanguageCode(theCodeSystem, refsetId);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION_STUDIO, ActivityType.UPDATE);
		ContentJob contentJob = new TranslationStudioContentJob(theCodeSystem,
				"Translation Studio language file import", refsetId)
				.addUpload(file.getInputStream(), file.getOriginalFilename());
		return jobService.queueContentJob(contentJob, refsetId, activity,
				job -> snolateTranslationService.importTranslationLanguageFile(
						theCodeSystem, refsetId, languageCode, job.getInputStream(),
						job.getInputFileOriginalName(), conceptColumn, termColumnList, importStatus));
	}

	@GetMapping("{refsetId}/sets/{label}/units/{conceptId}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public TranslationUnitRow getTranslationUnit(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@PathVariable String conceptId) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		TranslationUnitRow row = snolateTranslationService.getSampleRow(translationSet, conceptId);
		if (row != null) {
			row.blankLabels();
		}
		return row;
	}

	@PutMapping("{refsetId}/sets/{label}/units/{conceptId}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void updateTranslationUnit(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label, @PathVariable String conceptId,
			@RequestBody UpdateTranslationUnitRequest request) throws ServiceExceptionWithStatusCode {

		if (request == null || request.status() == null || request.status().isBlank()) {
			throw new ServiceExceptionWithStatusCode("Translation status is required.", HttpStatus.BAD_REQUEST);
		}
		TranslationStatus status;
		try {
			status = TranslationStatus.valueOf(request.status().trim());
		} catch (IllegalArgumentException e) {
			throw new ServiceExceptionWithStatusCode("Invalid translation status.", HttpStatus.BAD_REQUEST, e);
		}
		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateTranslationService.updateTranslationUnit(translationSet, conceptId, request.terms(), status);
	}

	@PostMapping("{refsetId}/sync-from-snowstorm")
	@Operation(summary = "Synchronise language refset terms from Snowstorm into Translation Studio.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob synchroniseFromSnowstorm(@PathVariable String codeSystem, @PathVariable String refsetId)
			throws ServiceException {

		rejectFoundationEnglishLangRefset(refsetId);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String languageCode = theCodeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset %s".formatted(refsetId), HttpStatus.CONFLICT);
		}

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION_STUDIO, ActivityType.UPDATE);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Translation Studio sync from Snowstorm", refsetId);
		return jobService.queueContentJob(contentJob, refsetId, activity, job -> {
			translationService.synchroniseWholeTranslationFromSnowstormToSnolate(theCodeSystem, snowstormClient, languageCode, refsetId);
			return new ChangeSummary();
		});
	}

	@PostMapping("{refsetId}/setup")
	@Operation(summary = "Link a language refset to Translation Studio: sync from Snowstorm then record branch metadata.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob setupTranslation(@PathVariable String codeSystem, @PathVariable String refsetId)
			throws ServiceException {

		rejectFoundationEnglishLangRefset(refsetId);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		String languageCode = theCodeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset %s".formatted(refsetId), HttpStatus.CONFLICT);
		}

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION_STUDIO, ActivityType.SNOLATE_LANGUAGE_INITIALISATION);
		ContentJob contentJob = new ContentJob(theCodeSystem, "Translation Studio translation setup", refsetId);
		return jobService.queueContentJob(contentJob, refsetId, activity, job -> {
			translationService.synchroniseWholeTranslationFromSnowstormToSnolate(theCodeSystem, snowstormClient, languageCode, refsetId);
			snowstormClient.addSnolateTranslationLanguage(refsetId, languageCode, theCodeSystem);
			return new ChangeSummary();
		});
	}

	@PostMapping("{refsetId}/sets/{label}/push")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob pushToSnowstorm(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody(required = false) APTaskRequest apTaskRequest) throws ServiceExceptionWithStatusCode {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);

		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION_STUDIO, ActivityType.UPDATE);
		final ContentJob pushJob = new ContentJob(theCodeSystem, "Translation Studio push to Snowstorm", refsetId);
		if (apTaskRequest == null) {
			apTaskRequest = new APTaskRequest();
		}
		String assigneeUsername = apTaskRequest.getAssigneeUsername();
		String taskTitle = apTaskRequest.getTaskTitle();
		pushJob.setTaskCreationCallable(() -> translationService.getCreateTranslationTask(theCodeSystem, assigneeUsername, taskTitle));

		return jobService.queueContentJob(pushJob, refsetId, activity, job -> {
			SnolateTranslationSet set = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
			ChangeSummary summary = translationService.synchroniseSnolateSubsetToSnowstorm(
					theCodeSystem, snowstormClient, set, job, job.getTaskCreationCallable());
			set.setLastPulled(new Date());
			snolateSetService.updateSet(set);
			return summary;
		});
	}

	@GetMapping("language-policy-questionnaire")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public LanguagePolicyQuestionnaire getLanguagePolicyQuestionnaire(@PathVariable String codeSystem) {
		return languagePolicyQuestionnaireService.getQuestionnaire();
	}

	@GetMapping("language-policy")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<LanguageTranslationPolicy> listLanguagePolicies(@PathVariable String codeSystem) {
		return languageTranslationPolicyService.findByCodeSystem(codeSystem);
	}

	@GetMapping("{refsetId}/language-policy")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public LanguageTranslationPolicy getLanguagePolicy(@PathVariable String codeSystem, @PathVariable String refsetId)
			throws ServiceExceptionWithStatusCode {
		return languageTranslationPolicyService.getOrThrow(codeSystem, refsetId);
	}

	@PutMapping("{refsetId}/language-policy")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public LanguageTranslationPolicy upsertLanguagePolicy(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestBody LanguageTranslationPolicyRequest request) throws ServiceException {
		return languageTranslationPolicyService.upsert(codeSystem, refsetId, request);
	}

	@PostMapping("{refsetId}/sets/{label}/ai-setup")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void translationSetAiSetup(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody AiSetupRequest request) throws ServiceExceptionWithStatusCode {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		translationSet.setAiGoldenSet(request.aiGoldenSet());
		snolateSetService.updateSet(translationSet);
	}

	@PostMapping("{refsetId}/sets/{label}/ai-suggestion")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Map<String, List<String>> aiGoldenSetSuggestion(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody List<String> englishTerm) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		return translationLLMService.suggestTranslations(translationSet, englishTerm, true, true);
	}

	@PostMapping("{refsetId}/sets/{label}/run-ai-batch")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void runAiBatchTranslate(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label,
			@RequestBody BatchTranslateRequest request) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateSetService.runAiBatchTranslate(translationSet, request);
	}

	@PostMapping("{refsetId}/sets/{label}/refresh")
	@Operation(summary = "Refresh a translation set by re-running its ECL selection.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public SnolateTranslationSet refreshSet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@PathVariable String label) throws ServiceException {

		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		return snolateSetService.refreshSet(translationSet);
	}

	@DeleteMapping("{refsetId}/sets/{label}")
	@Operation(summary = "Asynchronously delete a translation set.")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void deleteSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) throws ServiceException {
		SnolateTranslationSet translationSet = snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
		snolateSetService.deleteSet(translationSet);
	}
}
