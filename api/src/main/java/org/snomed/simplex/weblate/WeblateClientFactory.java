package org.snomed.simplex.weblate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class WeblateClientFactory {

	private final Cache<String, WeblateClient> clientCache;
	private final String url;
	private final SupportRegister supportRegister;

	public WeblateClientFactory(@Value("${weblate.url}") String url, SupportRegister supportRegister) {

		this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
		this.url = url;
		this.supportRegister = supportRegister;
	}

	public WeblateClient getClient() throws ServiceException {
		try {
			String authenticationToken = SecurityUtil.getAuthenticationToken();
			if (authenticationToken == null || authenticationToken.isEmpty()) {
				throw new ServiceExceptionWithStatusCode("Authentication token is missing. Unable to process request.", HttpStatus.FORBIDDEN);
			}
			return clientCache.get(authenticationToken, () -> newClient(authenticationToken));
		} catch (ExecutionException e) {
			throw new ServiceException("Failed to create Snowstorm client", e);
		}
	}

	private WeblateClient newClient(String authenticationToken) {
		RestTemplate restTemplate = new RestTemplateBuilder()
				.rootUri(url)
				.defaultHeader("Cookie", authenticationToken)
				.interceptors((request, body, execution) -> {
					request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
					return execution.execute(request, body);
				})
				.messageConverters(new MappingJackson2HttpMessageConverter())
				.build();
		return new WeblateClient(restTemplate, supportRegister);
	}
}
