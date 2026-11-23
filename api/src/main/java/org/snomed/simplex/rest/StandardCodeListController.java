package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.standardcodelist.StandardCodeListMetadata;
import org.snomed.simplex.standardcodelist.StandardCodeListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/standard-code-lists")
@Tag(name = "Standard Code Lists", description = "Reference code lists (e.g. EHDS Master Value Set Catalogue) usable as a source when creating a Translation Studio set.")
public class StandardCodeListController {

	private final StandardCodeListService standardCodeListService;

	public StandardCodeListController(StandardCodeListService standardCodeListService) {
		this.standardCodeListService = standardCodeListService;
	}

	@GetMapping
	@Operation(summary = "List the available standard code list catalogues.")
	public List<StandardCodeListMetadata> listCatalogues() {
		return standardCodeListService.listCatalogues();
	}

	@GetMapping("{catalogueId}")
	@Operation(summary = "Get a standard code list catalogue, including its list of value sets.")
	public StandardCodeListMetadata getCatalogue(@PathVariable String catalogueId) throws ServiceExceptionWithStatusCode {
		return standardCodeListService.getCatalogueOrThrow(catalogueId);
	}
}
