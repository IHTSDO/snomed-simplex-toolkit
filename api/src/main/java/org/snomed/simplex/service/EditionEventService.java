package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes server-side events to browsers viewing an edition (code system) using Server-Sent Events.
 * <p>
 * Subscriptions are held in memory, which is sufficient because Simplex runs as a single backend instance
 * per environment. Events are not stored: a browser that is not connected misses them, and relies on the jobs log
 * for the durable record.
 */
@Service
public class EditionEventService implements SmartLifecycle {

	public static final String EVENT_TRANSLATION_SYNC = "translation-sync";

	private static final long NO_TIMEOUT = 0L;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Map<String, List<SseEmitter>> emittersByCodeSystem = new ConcurrentHashMap<>();
	private volatile boolean running;

	public SseEmitter subscribe(String codeSystem) {
		SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
		if (!running) {
			emitter.complete();
			return emitter;
		}
		List<SseEmitter> emitters = emittersByCodeSystem.computeIfAbsent(codeSystem, key -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);
		Runnable remove = () -> emitters.remove(emitter);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(e -> remove.run());
		try {
			// Opens the stream straight away so the browser knows it is connected.
			emitter.send(SseEmitter.event().comment("connected"));
		} catch (IOException e) {
			remove.run();
		}
		return emitter;
	}

	public void publish(String codeSystem, String eventName, Object data) {
		List<SseEmitter> emitters = emittersByCodeSystem.get(codeSystem);
		if (emitters == null) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
			} catch (IOException | IllegalStateException e) {
				logger.debug("Dropping closed event stream for {}", codeSystem);
				emitters.remove(emitter);
			}
		}
	}

	/** Keeps idle connections open through proxies, and drops any that have silently gone away. */
	@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 25_000)
	void sendHeartbeats() {
		emittersByCodeSystem.forEach((codeSystem, emitters) -> {
			for (SseEmitter emitter : emitters) {
				try {
					emitter.send(SseEmitter.event().comment("keep-alive"));
				} catch (IOException | IllegalStateException e) {
					emitters.remove(emitter);
				}
			}
		});
	}

	@Override
	public void start() {
		running = true;
	}

	/**
	 * Close every open event stream when shutdown begins. Open streams count as in-flight requests, so without
	 * this the web server's graceful shutdown waits for its full timeout. The browser reconnects by itself.
	 */
	@Override
	public void stop() {
		running = false;
		emittersByCodeSystem.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
		emittersByCodeSystem.clear();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	/** Stop before the web server begins its graceful shutdown. */
	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}
}
