package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.CreateTranslationRequest;
import org.snomed.simplex.rest.pojos.LanguageCode;
import org.snomed.simplex.service.ActivityService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.TranslationService;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.JobType;
import org.snomed.simplex.weblate.WeblateSetService;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.List;

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
	private final WeblateSetService weblateSetService;

	public TranslationController(SnowstormClientFactory snowstormClientFactory, TranslationService translationService, ContentProcessingJobService jobService,
			ActivityService activityService, WeblateSetService weblateSetService) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.translationService = translationService;
		this.jobService = jobService;
		this.activityService = activityService;
		this.weblateSetService = weblateSetService;
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

	@GetMapping("{codeSystem}/translations/{refsetId}/weblate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<WeblateTranslationSet> listWeblateSets(@PathVariable String codeSystem, @PathVariable String refsetId) {
		return weblateSetService.findByCodeSystemAndRefset(codeSystem, refsetId);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public WeblateTranslationSet createWeblateSet(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestBody WeblateTranslationSet set) throws ServiceException {

		set.setCodesystem(codeSystem);
		set.setRefset(refsetId);
		return weblateSetService.createSet(set);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/weblate-set/{label}/pull-content")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob pullWeblateContent(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String label) {
		return DUMMY_COMPLETE;
	}

	@DeleteMapping("{codeSystem}/translations/{refsetId}/weblate-set/{weblateComponentSlug}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob deleteWeblateSet(@PathVariable String codeSystem, @PathVariable String refsetId, @PathVariable String weblateComponentSlug) {
		return DUMMY_COMPLETE;
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/weblate", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadTranslationFromWeblate(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			@RequestParam(defaultValue = "true") boolean translationTermsUseTitleCase) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		return jobService.queueContentJob(theCodeSystem, "Translation upload", file.getInputStream(), file.getOriginalFilename(), refsetId,
				activity, asyncJob -> translationService.uploadTranslationAsWeblateCSV(translationTermsUseTitleCase, asyncJob));
	}

	@PutMapping(path = "{codeSystem}/translations/{refsetId}/refset-tool", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public AsyncJob uploadTranslationFromRefsetAndTranslationTool(@PathVariable String codeSystem, @PathVariable String refsetId,
			@RequestParam MultipartFile file,
			@RequestParam(required = false, defaultValue = "true") boolean ignoreCaseInImport) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		Activity activity = new Activity(codeSystem, ComponentType.TRANSLATION, ActivityType.UPDATE);
		return jobService.queueContentJob(theCodeSystem, "Translation upload", file.getInputStream(), file.getOriginalFilename(), refsetId,
				activity, job -> translationService.uploadTranslationAsRefsetToolArchive(job, ignoreCaseInImport));
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
