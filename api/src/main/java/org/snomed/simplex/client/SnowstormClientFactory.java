package org.snomed.simplex.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class SnowstormClientFactory {

	public static final String SNOMEDCT_DERIVATIVES = "SNOMEDCT-DERIVATIVES";

	private final String snowstormUrl;
	private final SnowstormClient derivativesClient;

	private final Cache<String, SnowstormClient> clientCache;
	private final ObjectMapper objectMapper;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnowstormClientFactory(@Value("${snowstorm.url}") String snowstormUrl, @Value("${snowstorm.derivatives.url}") String snowstormAltDerivativesUrl) {
		this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
		this.objectMapper = (new ObjectMapper()).setSerializationInclusion(JsonInclude.Include.NON_NULL);
		this.snowstormUrl = snowstormUrl;
		logger.info("Snowstorm URL set as '{}'", snowstormUrl);
		if (!Strings.isNullOrEmpty(snowstormAltDerivativesUrl)) {
			logger.info("Snowstorm Derivatives URL set as '{}'", snowstormUrl);
			derivativesClient = new SnowstormClient(snowstormAltDerivativesUrl, null, objectMapper);
		} else {
			derivativesClient = null;
		}
	}

	public SnowstormClient getClient() throws ServiceExceptionWithStatusCode {
		try {
			String authenticationToken = getAuthToken();
			return clientCache.get(authenticationToken, () -> new SnowstormClient(snowstormUrl, authenticationToken, objectMapper));
		} catch (ExecutionException e) {
			throw new ServiceExceptionWithStatusCode("Failed to create Snowstorm client", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	public SnowstormClient getDerivativesClient() throws ServiceExceptionWithStatusCode {
		if (derivativesClient != null) {
			return derivativesClient;
		} else {
			return getClient();
		}
	}

	private static @NotNull String getAuthToken() throws ServiceExceptionWithStatusCode {
		String authenticationToken = SecurityUtil.getAuthenticationToken();
		if (authenticationToken == null || authenticationToken.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Authentication token is missing. Unable to process request.", HttpStatus.FORBIDDEN);
		}
		return authenticationToken;
	}
}
