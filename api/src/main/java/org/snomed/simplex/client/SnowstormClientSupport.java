package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.Map;
import org.springframework.web.client.HttpStatusCodeException;

import static java.lang.String.format;
import static org.snomed.simplex.client.SnowstormClient.CONCEPTS_COLLECTION_ENDPOINT;

/**
 * Helpers shared by the Snowstorm client classes.
 */
final class SnowstormClientSupport {

	private SnowstormClientSupport() {
	}

	static <T> T requireBody(T body, String type) throws ServiceExceptionWithStatusCode {
		if (body == null) {
			throw new ServiceExceptionWithStatusCode("Failed to fetch %s from Snowstorm.".formatted(type), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return body;
	}

	static ServiceExceptionWithStatusCode getServiceException(HttpStatusCodeException e, String action) {
		return new ServiceExceptionWithStatusCode(format("Failed to %s", action), HttpStatus.resolve(e.getStatusCode().value()), e);
	}

	static String conceptsCollectionPath(CodeSystem codeSystem) {
		return format(CONCEPTS_COLLECTION_ENDPOINT, codeSystem.getWorkingBranchPath());
	}

	static URI buildUriWithEcl(String rootUri, UriComponentsBuilder builder, String eclParamName, String ecl) {
		URI relative = builder.queryParam(eclParamName, "{ecl}").encode().buildAndExpand(Map.of("ecl", ecl)).toUri();
		// The relative URI is already fully encoded, so concatenate rather than re-encode
		return URI.create(rootUri + relative.toASCIIString());
	}
}
