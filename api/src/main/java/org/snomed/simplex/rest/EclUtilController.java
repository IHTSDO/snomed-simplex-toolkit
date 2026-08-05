package org.snomed.simplex.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("api/ecl")
@Tag(name = "ECL Builder", description = "Proxy Snowstorm ECL utility functions for the visual ECL builder.")
public class EclUtilController {

	private final SnowstormClientFactory snowstormClientFactory;

	public EclUtilController(SnowstormClientFactory snowstormClientFactory) {
		this.snowstormClientFactory = snowstormClientFactory;
	}

	@PostMapping(value = "string-to-model", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Parse an ECL string into a JSON model representation.")
	public JsonNode stringToModel(@RequestBody String ecl) throws ServiceExceptionWithStatusCode {
		return snowstormClientFactory.getClient().eclStringToModel(ecl);
	}

	@PostMapping(value = "model-to-string", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Convert an ECL JSON model representation into an ECL string.")
	public Map<String, String> modelToString(@RequestBody Object eclModel) throws ServiceExceptionWithStatusCode {
		return snowstormClientFactory.getClient().eclModelToString(eclModel);
	}

	@GetMapping(value = "domain-attributes", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Return MRCM domain attributes for a parent concept on a branch.")
	public Map<String, Object> getDomainAttributes(
			@RequestParam String branchPath,
			@RequestParam String parentIds,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws ServiceExceptionWithStatusCode {
		return snowstormClientFactory.getClient().getMrcmDomainAttributes(branchPath, parentIds, acceptLanguage);
	}
}
