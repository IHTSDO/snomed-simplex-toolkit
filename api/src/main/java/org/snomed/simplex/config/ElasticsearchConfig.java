package org.snomed.simplex.config;

import com.google.common.base.Strings;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.support.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Bean
	public ElasticsearchProperties elasticsearchProperties() {
		return new ElasticsearchProperties();
	}

	@Override
	public ClientConfiguration clientConfiguration() {
		ElasticsearchProperties elasticsearchProperties = elasticsearchProperties();
		final String[] urls = elasticsearchProperties.getUrls();
		for (String url : urls) {
			logger.info("Elasticsearch host: {}", url);
		}
		HttpHeaders apiKeyHeaders = new HttpHeaders();
		String apiKey = elasticsearchProperties.getApiKey();
		if (!Strings.isNullOrEmpty(apiKey)) {
			logger.info("Using API key authentication.");
			apiKeyHeaders.add("Authorization", "ApiKey " + apiKey);
		}

		if (useHttps(urls)) {
			return ClientConfiguration.builder()
					.connectedTo(getHosts(urls))
					.usingSsl()
					.withDefaultHeaders(apiKeyHeaders)
					.withClientConfigurer(configureHttpClient(elasticsearchProperties))
					.build();
		} else {
			return ClientConfiguration.builder()
					.connectedTo(getHosts(urls))
					.withDefaultHeaders(apiKeyHeaders)
					.withClientConfigurer(configureHttpClient(elasticsearchProperties))
					.build();
		}
	}

	private boolean useHttps(String[] urls) {
		for (String url : urls) {
			if (url.startsWith("https://")) {
				return true;
			}
		}
		return false;
	}

	private static String[] getHosts(String[] hosts) {
		List<HttpHost> httpHosts = new ArrayList<>();
		for (String host : hosts) {
			httpHosts.add(HttpHost.create(host));
		}
		return httpHosts.stream().map(HttpHost::toHostString).toList().toArray(new String[]{});
	}

	private ElasticsearchClients.ElasticsearchRestClientConfigurationCallback configureHttpClient(ElasticsearchProperties elasticsearchProperties) {
		String elasticsearchUsername = elasticsearchProperties.getUsername();
		String elasticsearchPassword = elasticsearchProperties.getPassword();

		return ElasticsearchClients.ElasticsearchRestClientConfigurationCallback.from(clientBuilder -> {
			clientBuilder.setRequestConfigCallback(builder -> {
				builder.setConnectionRequestTimeout(0);//Disable lease handling for the connection pool! See https://github.com/elastic/elasticsearch/issues/24069
				return builder;
			});
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			if (!Strings.isNullOrEmpty(elasticsearchUsername) && !Strings.isNullOrEmpty(elasticsearchPassword)) {
				credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));
			}
			clientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
				httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
				httpClientBuilder.addInterceptorLast(this::logFailedResponse);
				return httpClientBuilder;
			});
			return clientBuilder;
		});
	}

	private void logFailedResponse(HttpResponse response, HttpContext context) {
		int status = response.getStatusLine().getStatusCode();
		if (status < 400) {
			return;
		}
		try {
			HttpEntity entity = response.getEntity();
			String body = readResponseBody(entity);
			HttpRequest request = (HttpRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
			String method = request != null ? request.getRequestLine().getMethod() : "UNKNOWN";
			String uri = request != null ? request.getRequestLine().getUri() : "UNKNOWN";
			if (body != null) {
				logger.error("Elasticsearch HTTP failure: method={} uri={} status={} body={}", method, uri, status, body);
				response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
			} else {
				logger.error("Elasticsearch HTTP failure: method={} uri={} status={} (response body unavailable)", method, uri, status);
			}
		} catch (Exception e) {
			logger.error("Failed to log Elasticsearch HTTP failure response", e);
		}
	}

	private String readResponseBody(HttpEntity entity) {
		if (entity == null) {
			return null;
		}
		try {
			return EntityUtils.toString(entity, StandardCharsets.UTF_8);
		} catch (IllegalStateException e) {
			return null;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to read Elasticsearch HTTP failure response body", e);
		}
	}

}
