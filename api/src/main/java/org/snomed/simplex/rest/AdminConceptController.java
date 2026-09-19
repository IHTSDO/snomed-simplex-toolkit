package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.AdminConceptEditorDetail;
import org.snomed.simplex.rest.pojos.AdminConceptUpdateRequest;
import org.snomed.simplex.service.AdminConceptEditorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/admin/{codeSystem}/concepts")
@Tag(name = "Admin Concept Editor", description = "Admin concept description editing")
public class AdminConceptController {

	private final AdminConceptEditorService adminConceptEditorService;
	private final SnowstormClientFactory snowstormClientFactory;

	public AdminConceptController(AdminConceptEditorService adminConceptEditorService,
			SnowstormClientFactory snowstormClientFactory) {
		this.adminConceptEditorService = adminConceptEditorService;
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@GetMapping("/{conceptId}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Load a concept for admin description editing")
	public AdminConceptEditorDetail getConceptForEditor(@PathVariable String codeSystem, @PathVariable String conceptId)
			throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return adminConceptEditorService.loadForEditor(theCodeSystem, snowstormClient, conceptId);
	}

	@PutMapping("/{conceptId}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	@Operation(summary = "Save admin description edits for a concept")
	public AdminConceptEditorDetail updateConceptDescriptions(@PathVariable String codeSystem, @PathVariable String conceptId,
			@RequestBody AdminConceptUpdateRequest request) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem theCodeSystem = snowstormClient.getCodeSystemOrThrow(codeSystem);
		return adminConceptEditorService.applyDescriptionEdits(theCodeSystem, snowstormClient, conceptId, request);
	}
}
