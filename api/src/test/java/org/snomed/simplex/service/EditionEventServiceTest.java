package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.rest.EditionEventsController;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Drives the service through the real controller using MockMvc, which is the only way to give an
 * {@code SseEmitter} a live response to write to.
 */
class EditionEventServiceTest {

	private EditionEventService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = new EditionEventService();
		service.start();
		mockMvc = MockMvcBuilders.standaloneSetup(new EditionEventsController(service)).build();
	}

	private MvcResult subscribe(String codeSystem) throws Exception {
		return mockMvc.perform(get("/api/" + codeSystem + "/events").accept(MediaType.TEXT_EVENT_STREAM))
				.andReturn();
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	@Test
	void subscribeOpensStreamAndConfirmsConnection() throws Exception {
		MvcResult result = subscribe("SNOMEDCT-BE");

		assertTrue(result.getRequest().isAsyncStarted());
		assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, result.getResponse().getContentType());
		assertTrue(body(result).contains(":connected"));
	}

	@Test
	void publishDeliversNamedEventWithJsonDataToSubscribersOfThatCodeSystem() throws Exception {
		MvcResult subscriber = subscribe("SNOMEDCT-BE");

		service.publish("SNOMEDCT-BE", EditionEventService.EVENT_TRANSLATION_SYNC,
				Map.of("trigger", "project rebase", "status", "COMPLETE"));

		String content = body(subscriber);
		assertTrue(content.contains("event:translation-sync"), content);
		assertTrue(content.contains("\"trigger\":\"project rebase\""), content);
		assertTrue(content.contains("\"status\":\"COMPLETE\""), content);
	}

	@Test
	void publishDoesNotReachSubscribersOfOtherCodeSystems() throws Exception {
		MvcResult other = subscribe("SNOMEDCT-US");

		service.publish("SNOMEDCT-BE", EditionEventService.EVENT_TRANSLATION_SYNC, Map.of("status", "COMPLETE"));

		assertFalse(body(other).contains("translation-sync"));
	}

	@Test
	void publishReachesEverySubscriberOfTheCodeSystem() throws Exception {
		MvcResult first = subscribe("SNOMEDCT-BE");
		MvcResult second = subscribe("SNOMEDCT-BE");

		service.publish("SNOMEDCT-BE", EditionEventService.EVENT_TRANSLATION_SYNC, Map.of("status", "COMPLETE"));

		assertTrue(body(first).contains("translation-sync"));
		assertTrue(body(second).contains("translation-sync"));
	}

	@Test
	void publishWithNoSubscribersDoesNothing() {
		assertDoesNotThrow(() -> service.publish("SNOMEDCT-BE", EditionEventService.EVENT_TRANSLATION_SYNC, Map.of()));
	}

	@Test
	void heartbeatKeepsStreamsOpen() throws Exception {
		MvcResult subscriber = subscribe("SNOMEDCT-BE");

		service.sendHeartbeats();

		assertTrue(body(subscriber).contains(":keep-alive"));
	}

	@Test
	void stopClosesOpenStreamsAndForgetsThem() throws Exception {
		MvcResult subscriber = subscribe("SNOMEDCT-BE");
		String before = body(subscriber);

		service.stop();
		service.publish("SNOMEDCT-BE", EditionEventService.EVENT_TRANSLATION_SYNC, Map.of("status", "COMPLETE"));

		assertFalse(service.isRunning());
		assertEquals(before, body(subscriber), "Nothing should be sent to a stream closed by shutdown");
	}

	@Test
	void subscribeAfterStopIsClosedImmediatelyWithoutEvents() throws Exception {
		service.stop();

		MvcResult late = subscribe("SNOMEDCT-BE");
		service.publish("SNOMEDCT-BE", EditionEventService.EVENT_TRANSLATION_SYNC, Map.of("status", "COMPLETE"));

		assertEquals("", body(late));
	}

	@Test
	void shutdownRunsBeforeTheWebServerStops() {
		assertEquals(Integer.MAX_VALUE, service.getPhase());
	}
}
