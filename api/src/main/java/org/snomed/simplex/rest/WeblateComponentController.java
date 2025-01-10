package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.WeblateService;
import org.snomed.simplex.weblate.domain.WeblateRecord;
import org.snomed.simplex.weblate.domain.WeblateSet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Weblate Util", description = "Tools for maintaining weblate")
@RequestMapping("api/weblate")
public class WeblateComponentController {

	private final WeblateService weblateService;

	public WeblateComponentController(WeblateService weblateService) {
		this.weblateService = weblateService;
	}

	@GetMapping("shared-sets")
	public Page<WeblateSet> getSharedSets() {
		return new Page<>(List.of(
				new WeblateSet("Allergies", "allergies", "Shared"),
				new WeblateSet("Clinical findings", "clinical_findings", "Shared")
				));
	}

	@PostMapping("shared-sets")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public WeblateSet createSharedSet(@RequestBody WeblateSet weblateSet, @RequestParam String ecl) {
		return weblateSet;
	}

	@PostMapping("shared-sets/{slug}/refresh")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void refreshSharedSet(@PathVariable String slug, @RequestParam String ecl) {

	}

	@GetMapping("shared-sets/{slug}/records")
	public Page<WeblateRecord> getSharedSetRecords(@PathVariable String slug,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit) {

		return new Page<>(List.of(new WeblateRecord("Allergy to soy protein", "Ofnæmi fyrir sojapróteini", "782594005", "")));
	}

	@PutMapping("shared-sets/{slug}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void updateSharedSet(@PathVariable String slug, @RequestBody WeblateSet weblateSet) {
	}

	@DeleteMapping("shared-sets/{slug}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void deleteSharedSet(@PathVariable String slug) {
	}

	@GetMapping(value = "/component-csv", produces = "text/csv")
	public void createCollection(@RequestParam String branch, @RequestParam String valueSetEcl,
								 HttpServletResponse response) throws ServiceException, IOException {

		response.setContentType("text/csv");
		weblateService.createConceptSet(branch, valueSetEcl, response.getOutputStream());
	}

}
