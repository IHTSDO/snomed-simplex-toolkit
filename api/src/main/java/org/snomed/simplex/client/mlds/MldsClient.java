package org.snomed.simplex.client.mlds;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.simplex.client.mlds.domain.*;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MldsClient {

	private static final Logger logger = LoggerFactory.getLogger(MldsClient.class);

	private final RestTemplate restTemplate;

	public MldsClient(@Value("${mlds.api-url}") String mldsApiUrl) {
		ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
		ClientHttpRequestInterceptor authenticationInterceptor = (request, body, execution) -> {
			try {
				String authenticationToken = getAuthenticationToken();
				request.getHeaders().add(HttpHeaders.COOKIE, authenticationToken);
			} catch (ServiceExceptionWithStatusCode e) {
				throw new RestClientException(e.getMessage(), e);
			}
			return execution.execute(request, body);
		};
		ClientHttpRequestInterceptor loggingInterceptor = (request, body, execution) -> {
			if (logger.isTraceEnabled()) {
				logger.trace("MLDS request {} {}", request.getMethod(), request.getURI());
				request.getHeaders().forEach((name, values) ->
						values.forEach(value -> logger.trace("MLDS request header: {}={}", name, value)));
			}
			var response = execution.execute(request, body);
			if (logger.isTraceEnabled()) {
				logger.trace("MLDS response status: {}", response.getStatusCode());
			}
			return response;
		};

		this.restTemplate = new RestTemplateBuilder()
				.rootUri(mldsApiUrl)
				.interceptors(authenticationInterceptor, loggingInterceptor)
				.messageConverters(jsonConverter, new StringHttpMessageConverter())
				.build();
	}

	public String fetchFeed() throws ServiceExceptionWithStatusCode {
		return exchange(HttpMethod.GET, "/api/feed", null, String.class);
	}

	public MldsReleasePackageResponse getReleasePackage(long releasePackageId) throws ServiceExceptionWithStatusCode {
		return exchange(
				HttpMethod.GET,
				"/api/releasePackages/" + releasePackageId,
				null,
				MldsReleasePackageResponse.class);
	}

	public MldsReleaseVersionResponse createReleaseVersion(long releasePackageId, MldsReleaseVersionRequest request)
			throws ServiceExceptionWithStatusCode {
		return exchange(
				HttpMethod.POST,
				"/api/releasePackages/" + releasePackageId + "/releaseVersions",
				request,
				MldsReleaseVersionResponse.class);
	}

	public MldsReleaseFileResponse createReleaseFile(long releasePackageId, long releaseVersionId, MldsReleaseFileRequest request)
			throws ServiceExceptionWithStatusCode {
		return exchange(
				HttpMethod.POST,
				"/api/releasePackages/" + releasePackageId + "/releaseVersions/" + releaseVersionId + "/releaseFiles",
				request,
				MldsReleaseFileResponse.class);
	}

	private <T> T exchange(HttpMethod method, String path, Object body, Class<T> responseType) throws ServiceExceptionWithStatusCode {
		try {
			HttpEntity<Object> entity = body != null ? new HttpEntity<>(body) : new HttpEntity<>(null);
			ResponseEntity<T> response = restTemplate.exchange(path, method, entity, responseType);
			return response.getBody();
		} catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
			throw new ServiceExceptionWithStatusCode("MLDS rejected credentials — staff access required.", HttpStatus.FORBIDDEN, e);
		} catch (HttpClientErrorException e) {
			throw new ServiceExceptionWithStatusCode("MLDS request failed: " + e.getStatusCode(), HttpStatus.valueOf(e.getStatusCode().value()), e);
		} catch (RestClientException e) {
			if (e.getCause() instanceof ServiceExceptionWithStatusCode serviceException) {
				throw serviceException;
			}
			throw new ServiceExceptionWithStatusCode("MLDS request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY, e);
		}
	}

	private static String getAuthenticationToken() throws ServiceExceptionWithStatusCode {
		String authenticationToken = SecurityUtil.getAuthenticationToken();
		if (authenticationToken == null || authenticationToken.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Authentication token is missing. Unable to process request.", HttpStatus.FORBIDDEN);
		}
		return authenticationToken;
	}
}
