package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.CreateSnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateSetService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Translation Snolate", description = "Snolate translation subset definitions and jobs.")
@RequestMapping("api")
public class TranslationSnolateController {

	private final SnowstormClientFactory snowstormClientFactory;
	private final SnolateSetService snolateSetService;

	public TranslationSnolateController(SnowstormClientFactory snowstormClientFactory, SnolateSetService snolateSetService) {
		this.snowstormClientFactory = snowstormClientFactory;
		this.snolateSetService = snolateSetService;
	}

	@GetMapping("{codeSystem}/translations/snolate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSet> listAllSnolateSets(@PathVariable String codeSystem) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.getCodeSystemOrThrow(codeSystem);
		return snolateSetService.findByCodeSystem(codeSystem);
	}

	@GetMapping("{codeSystem}/translations/{refsetId}/snolate-set")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public List<SnolateTranslationSet> listSnolateSets(@PathVariable String codeSystem, @PathVariable String refsetId)
			throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		snowstormClient.getRefsetOrThrow(refsetId, theCodeSystem);
		return snolateSetService.findByCodeSystemAndRefset(codeSystem, refsetId);
	}

	@PostMapping("{codeSystem}/translations/{refsetId}/snolate-set")
	@Operation(summary = "Create a new Snolate translation set for a language refset.",
			description = "Queues membership of concepts from ECL in TranslationSource.sets. The 'label' parameter must be a lowercase URL-friendly string using characters [a-z0-9_-]. The 'name' parameter is the human-readable display name for the translation set.")
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
		return snolateSetService.findSubsetOrThrow(codeSystem, refsetId, label);
	}
}
