package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.WeblateService;
import org.snomed.simplex.weblate.domain.WeblateSet;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Weblate Translation Management")
@RequestMapping("api/weblate")
public class WeblateComponentController {

	private final WeblateService weblateService;

	public WeblateComponentController(WeblateService weblateService) {
		this.weblateService = weblateService;
	}

	@GetMapping("shared-sets")
	public Page<WeblateSet> getSharedSets() {
		return weblateService.getSharedSets();
	}

	@PostMapping("shared-sets")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void createSharedSet(@RequestBody WeblateSet weblateSet) throws ServiceException {
		weblateService.createSharedSet(weblateSet);
	}

	@PostMapping("shared-sets/{slug}/refresh")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void refreshSharedSet(@PathVariable String slug, @RequestParam String ecl) {
		// Code stub to enable UI development
	}

	@GetMapping("shared-sets/{slug}/records")
	public Page<WeblateUnit> getSharedSetRecords(@PathVariable String slug,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit) {

		return new Page<>(List.of(new WeblateUnit(List.of("Allergy to soy protein"), List.of("Ofnæmi fyrir sojapróteini"), "782594005",
				"http://snomed.info/id/782594005 - Allergy to soy protein (finding)", "")));
	}

	@PutMapping("shared-sets/{slug}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void updateSharedSet(@PathVariable String slug, @RequestBody WeblateSet weblateSet) {
		// Code stub to enable UI development
	}

	@DeleteMapping("shared-sets/{slug}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void deleteSharedSet(@PathVariable String slug) {
		// Code stub to enable UI development
	}

	@GetMapping(value = "/component-csv", produces = "text/csv")
	public void createCollection(@RequestParam String branch, @RequestParam String valueSetEcl,
								 HttpServletResponse response) throws ServiceException, IOException {

		response.setContentType("text/csv");
		weblateService.createConceptSet(branch, valueSetEcl, response.getOutputStream());
	}

}
