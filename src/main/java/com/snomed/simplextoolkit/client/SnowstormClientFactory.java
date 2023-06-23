package com.snomed.simplextoolkit.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class SnowstormClientFactory {

	@Value("${snowstorm.url}")
	private String snowstormUrl;

	private final Cache<String, SnowstormClient> clientCache;
	private final ObjectMapper objectMapper;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnowstormClientFactory() {
		this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
		this.objectMapper = (new ObjectMapper()).setSerializationInclusion(JsonInclude.Include.NON_NULL);
		logger.info("Snowstorm URL set as '{}'", snowstormUrl);
	}

	public SnowstormClient getClient() throws ServiceException {
		try {
			String authenticationToken = getAuthenticationToken();
			return clientCache.get(authenticationToken, () -> new SnowstormClient(snowstormUrl, authenticationToken, objectMapper));
		} catch (ExecutionException e) {
			throw new ServiceException("Failed to create Snowstorm client", e);
		}
	}

	private String getAuthenticationToken() {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		if (securityContext != null) {
			Authentication authentication = securityContext.getAuthentication();
			if (authentication != null) {
				return (String) authentication.getCredentials();
			}
		}
		return null;
	}
}
