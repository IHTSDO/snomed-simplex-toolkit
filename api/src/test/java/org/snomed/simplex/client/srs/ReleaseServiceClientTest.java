package org.snomed.simplex.client.srs;

import com.google.common.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.client.srs.domain.SRSProduct;
import org.snomed.simplex.client.srs.manifest.ReleaseManifestService;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceClientTest {

	private static final String RELEASE_SERVICE_URL = "http://release.test";
	private static final String USERNAME = "srs-user";
	private static final String RELEASE_CENTER = "simplex";

	@Mock
	private ReleaseManifestService releaseManifestService;

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private AuthenticationClient authenticationClient;

	private MockRestServiceServer server;
	private ReleaseServiceClient client;

	@BeforeEach
	void setUp() throws Exception {
		RestTemplate restTemplate = new RestTemplateBuilder().rootUri(RELEASE_SERVICE_URL).build();
		server = MockRestServiceServer.bindTo(restTemplate).build();

		client = new ReleaseServiceClient(
				RELEASE_SERVICE_URL,
				USERNAME,
				"pass",
				RELEASE_CENTER,
				"branch",
				"source",
				"header {simplexProduct} {simplexProductOrganisationName} {simplexProductContactDetails} {readmeEndDate}",
				"licence {simplexProduct} {simplexProductOrganisationName} {simplexProductContactDetails} {readmeEndDate}",
				releaseManifestService,
				snowstormClientFactory,
				authenticationClient);

		@SuppressWarnings("unchecked")
		Cache<String, RestTemplate> cache = (Cache<String, RestTemplate>) ReflectionTestUtils.getField(ReleaseServiceClient.class, "clientCache");
		cache.invalidateAll();
		cache.put(USERNAME, restTemplate);
	}

	@Test
	void getProduct_forbidden_returnsServiceUnavailableWithoutHtml() {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-RELTEST-SEPT26", "MAIN/test");
		server.expect(requestTo(RELEASE_SERVICE_URL + "/centers/simplex/products/snomedctreltestsept26"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.FORBIDDEN)
						.contentType(MediaType.TEXT_HTML)
						.body("<html><head><title>403 Forbidden</title></head><body>nginx</body></html>"));

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class, () -> client.getProduct(codeSystem));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getStatusCode());
		assertNotNull(exception.getMessage());
		assertEquals(true, exception.getMessage().contains("loading release product"));
		assertEquals(false, exception.getMessage().toLowerCase().contains("<html>"));
		server.verify();
	}

	@Test
	void getProduct_notFound_returnsNull() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/test");
		server.expect(requestTo(RELEASE_SERVICE_URL + "/centers/simplex/products/snomedcttest"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertNull(client.getProduct(codeSystem));
		server.verify();
	}

	@Test
	void getProduct_ok_returnsProduct() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/test");
		server.expect(requestTo(RELEASE_SERVICE_URL + "/centers/simplex/products/snomedcttest"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"id\":\"product-1\"}", MediaType.APPLICATION_JSON));

		SRSProduct product = client.getProduct(codeSystem);

		assertNotNull(product);
		assertEquals("product-1", product.getId());
		server.verify();
	}

	@Test
	void createBuild_serviceError_returnsServiceUnavailable() {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/test");
		server.expect(requestTo(RELEASE_SERVICE_URL + "/centers/simplex/products/snomedcttest/builds"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.FORBIDDEN)
						.contentType(MediaType.TEXT_HTML)
						.body("<html><body>403 Forbidden</body></html>"));

		ServiceExceptionWithStatusCode exception = assertThrows(ServiceExceptionWithStatusCode.class,
				() -> client.createBuild(codeSystem, "20260731"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getStatusCode());
		assertEquals(true, exception.getMessage().contains("creating release build"));
		server.verify();
	}

	@Test
	void createBuild_ok_returnsBuild() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/test");
		server.expect(requestTo(RELEASE_SERVICE_URL + "/centers/simplex/products/snomedcttest/builds"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"id":"build-1","url":"http://release.test/build-1","creationTime":"t","status":"PENDING","tags":[],"configuration":{"effectiveTime":"20260731"}}
						""", MediaType.APPLICATION_JSON));

		SRSBuild build = client.createBuild(codeSystem, "20260731");

		assertNotNull(build);
		assertEquals("build-1", build.id());
		server.verify();
	}

	@Test
	void sanitizeResponseBodyForLog_htmlBody() {
		assertEquals("(HTML error page)", ReleaseServiceClient.sanitizeResponseBodyForLog("<html><body>403</body></html>"));
	}
}
