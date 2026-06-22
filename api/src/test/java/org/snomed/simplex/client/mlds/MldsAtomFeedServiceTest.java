package org.snomed.simplex.client.mlds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MldsAtomFeedServiceTest {

	private MldsAtomFeedService service;

	@BeforeEach
	void setUp() {
		service = new MldsAtomFeedService(mock(MldsClient.class));
	}

	@Test
	void parseReleasePackageId_findsMatchingModule() throws Exception {
		String feedXml = readResource("/mlds-feed-snippet.xml");
		Optional<Long> releasePackageId = service.parseReleasePackageId(feedXml, "http://snomed.info/sct/11000279109");
		assertTrue(releasePackageId.isPresent());
		assertEquals(1422972L, releasePackageId.get());
	}

	@Test
	void parseReleasePackageId_returnsEmptyWhenModuleNotFound() throws Exception {
		String feedXml = readResource("/mlds-feed-snippet.xml");
		Optional<Long> releasePackageId = service.parseReleasePackageId(feedXml, "http://snomed.info/sct/99999999999");
		assertTrue(releasePackageId.isEmpty());
	}

	@Test
	void findReleasePackageId_usesFeedFromClient() throws ServiceExceptionWithStatusCode {
		MldsClient mldsClient = mock(MldsClient.class);
		when(mldsClient.fetchFeed()).thenReturn(readResource("/mlds-feed-snippet.xml"));
		MldsAtomFeedService feedService = new MldsAtomFeedService(mldsClient);

		Optional<Long> releasePackageId = feedService.findReleasePackageId("http://snomed.info/sct/11000279109");
		assertTrue(releasePackageId.isPresent());
		assertEquals(1422972L, releasePackageId.get());
	}

	private static String readResource(String path) {
		try (var inputStream = MldsAtomFeedServiceTest.class.getResourceAsStream(path)) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
