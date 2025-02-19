package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.weblate.WeblateService;
import org.snomed.simplex.weblate.domain.WeblateComponent;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@Tag(name = "Weblate Translation Management")
@RequestMapping("api/weblate")
public class WeblateComponentController {

	private final WeblateService weblateService;

	public WeblateComponentController(WeblateService weblateService) {
		this.weblateService = weblateService;
	}

	@GetMapping("shared-components")
	public Page<WeblateComponent> getSharedSets() throws ServiceException {
		return weblateService.getSharedSets();
	}

	@PostMapping("shared-components")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void createSharedSet(@RequestBody WeblateComponent weblateComponent) throws ServiceException {
		weblateService.createSharedSet(weblateComponent);
	}

	@PostMapping("shared-components/{slug}/refresh")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void refreshSharedSet(@PathVariable String slug, @RequestParam String ecl) {
		// Code stub to enable UI development
	}

	@GetMapping("shared-components/{slug}/records")
	public Page<WeblateUnit> getSharedSetRecords(@PathVariable String slug,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit) throws ServiceException {

		return weblateService.getSharedSetRecords(slug);
	}

	@PutMapping("shared-components/{slug}")
	@PreAuthorize("hasPermission('ADMIN', '')")
	public void updateSharedSet(@PathVariable String slug, @RequestBody WeblateComponent weblateComponent) {
		// Code stub to enable UI development
	}

	@DeleteMapping("shared-components/{slug}")
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
