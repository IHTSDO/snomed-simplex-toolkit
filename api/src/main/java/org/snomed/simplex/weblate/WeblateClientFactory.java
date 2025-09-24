package org.snomed.simplex.weblate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class WeblateClientFactory {

	private final Cache<String, WeblateClient> clientCache;
	private final String url;
	private final SupportRegister supportRegister;
	private WeblateAdminClient adminClient;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	// Fields for admin client recreation
	private long adminClientCreationTime;
	private final AuthenticationClient authenticationClient;
	private final String adminUsername;
	private final String adminPassword;

	public WeblateClientFactory(@Value("${weblate.url}") String url, SupportRegister supportRegister,
			@Value("${weblate.admin.username}") String adminUsername, @Value("${weblate.admin.password}") String adminPassword,
			AuthenticationClient authenticationClient) throws ServiceException {

		this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
		this.url = url;
		this.supportRegister = supportRegister;
		this.authenticationClient = authenticationClient;
		this.adminUsername = adminUsername;
		this.adminPassword = adminPassword;
		this.adminClient = createAdminClient(authenticationClient, adminUsername, adminPassword);
		this.adminClientCreationTime = System.currentTimeMillis();
	}

	public WeblateClient getClient() throws ServiceExceptionWithStatusCode {
		try {
			String authenticationToken = SecurityUtil.getAuthenticationToken();
			if (authenticationToken == null || authenticationToken.isEmpty()) {
				throw new ServiceExceptionWithStatusCode("Authentication token is missing. Unable to process request.", HttpStatus.FORBIDDEN);
			}
			return clientCache.get(authenticationToken, () -> new WeblateClient(getRestTemplate(authenticationToken), supportRegister));
		} catch (ExecutionException e) {
			throw new ServiceExceptionWithStatusCode("Failed to create Snowstorm client", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	WeblateAdminClient getAdminClient() {
		long currentTime = System.currentTimeMillis();
		long timeSinceCreation = currentTime - adminClientCreationTime;

		// If more than 10 minutes have passed, recreate the admin client
		if (timeSinceCreation > TimeUnit.MINUTES.toMillis(10)) {
			synchronized (this) {
				// Double-check pattern to avoid race conditions
				if (currentTime - adminClientCreationTime > TimeUnit.MINUTES.toMillis(10)) {
					try {
						adminClient = createAdminClient(authenticationClient, adminUsername, adminPassword);
						adminClientCreationTime = currentTime;
						logger.info("Admin client recreated after {} minutes", TimeUnit.MILLISECONDS.toMinutes(timeSinceCreation));
					} catch (ServiceException e) {
						logger.error("Failed to recreate admin client", e);
						// Return the existing client if recreation fails
					}
				}
			}
		}

		return adminClient;
	}

	private WeblateAdminClient createAdminClient(AuthenticationClient authenticationClient, String adminUsername, String adminPassword) throws ServiceException {
		try {
			String weblateAdminAuthenticationToken = authenticationClient.fetchAuthenticationToken(adminUsername, adminPassword);
			return new WeblateAdminClient(getRestTemplate(weblateAdminAuthenticationToken));
		} catch (HttpClientErrorException.BadRequest e) {
			String message = "Failed to create Weblate admin client. Check IMS is up and Weblate admin credentials.";
			logger.error(message);
			throw new ServiceExceptionWithStatusCode(message, HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	private RestTemplate getRestTemplate(String authenticationToken) {
		return new RestTemplateBuilder()
				.rootUri(url)
				.defaultHeader("Cookie", authenticationToken)
				.build();
	}

	public String getApiUrl() {
		return url;
	}
}
