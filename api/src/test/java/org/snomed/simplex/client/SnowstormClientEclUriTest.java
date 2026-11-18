package org.snomed.simplex.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.snomed.simplex.client.domain.CodeSystem;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SnowstormClientEclUriTest {

	private static final String ROOT = "https://snowstorm.example.org/snowstorm/snomed-ct";

	private static final List<String> ECL_EXPRESSIONS = List.of(
			"<900000000000506000",
			">73211009",
			"<< 73211009 |Diabetes mellitus|",
			"^ 723264001 {{ C active=true }}",
			"<< 73211009 {{ +HISTORY }}",
			"<< 73211009 {{ +HISTORY-MOD }}",
			"<< 73211009 {{ term = \"a+b%c\" }}");

	@Test
	void eclIsEncodedExactlyOnce() {
		for (String ecl : ECL_EXPRESSIONS) {
			URI uri = build(ecl);
			String rawEcl = rawEclValue(uri);

			// Decoding once (as Snowstorm does) must give back the original ECL, not a still-encoded string.
			assertEquals(ecl, URLDecoder.decode(rawEcl, StandardCharsets.UTF_8), "Round trip failed for: " + ecl);
			// A literal percent in the ECL must itself be encoded (%25), never passed through.
			assertFalse(rawEcl.matches(".*%(?![0-9A-F]{2}).*"), "Unencoded percent in: " + rawEcl);
		}
	}

	@Test
	void reservedCharactersAreEncoded() {
		String rawEcl = rawEclValue(build("<< 73211009 {{ +HISTORY }}"));

		assertEquals("%3C%3C%2073211009%20%7B%7B%20%2BHISTORY%20%7D%7D", rawEcl);
	}

	@Test
	void otherParametersAndPathAreKept() {
		URI uri = SnowstormClientSupport.buildUriWithEcl(ROOT,
				UriComponentsBuilder.fromPath("/browser/MAIN/SNOMEDCT-BE/members").queryParam("active", true).queryParam("limit", 10),
				"referenceSet", "<900000000000506000");

		assertEquals("https", uri.getScheme());
		assertEquals("snowstorm.example.org", uri.getHost());
		assertEquals("/snowstorm/snomed-ct/browser/MAIN/SNOMEDCT-BE/members", uri.getRawPath());
		assertEquals("active=true&limit=10&referenceSet=%3C900000000000506000", uri.getRawQuery());
	}

	@Test
	void uriIsNotTemplateExpandedByRestTemplate() {
		// RestTemplate only template-expands String URLs; a URI is used as is.
		URI uri = build("<< 73211009 {{ +HISTORY }}");
		assertFalse(uri.toString().contains("%25"), "Double encoding detected: " + uri);
	}

	private static URI build(String ecl) {
		return SnowstormClientSupport.buildUriWithEcl(ROOT,
				UriComponentsBuilder.fromPath("/MAIN/SNOMEDCT-BE/concepts").queryParam("limit", 10), "ecl", ecl);
	}

	private static String rawEclValue(URI uri) {
		for (String pair : uri.getRawQuery().split("&")) {
			if (pair.startsWith("ecl=") || pair.startsWith("referenceSet=")) {
				return pair.substring(pair.indexOf('=') + 1);
			}
		}
		throw new AssertionError("No ecl param in " + uri);
	}

	@Test
	void getConceptsSendsSingleEncodedEclToConfiguredHost() throws Exception {
		AtomicReference<String> requestUri = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			requestUri.set(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
			byte[] body = "{\"items\":[],\"total\":0}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			SnowstormClient client = new SnowstormClient("http://localhost:" + server.getAddress().getPort() + "/snowstorm/snomed-ct",
					null, "test", new ObjectMapper(), 10);
			CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");

			client.getConcepts("<< 73211009 {{ +HISTORY }}", codeSystem, null);

			assertEquals("/snowstorm/snomed-ct/MAIN/SNOMEDCT-BE/concepts?limit=10000&ecl=%3C%3C%2073211009%20%7B%7B%20%2BHISTORY%20%7D%7D", requestUri.get());
		} finally {
			server.stop(0);
		}
	}
}
