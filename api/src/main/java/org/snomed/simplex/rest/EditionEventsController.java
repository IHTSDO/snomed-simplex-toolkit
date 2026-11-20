package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.simplex.service.EditionEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("api/{codeSystem}/events")
@Tag(name = "Events", description = "Server-sent events for an edition")
public class EditionEventsController {

	private final EditionEventService editionEventService;

	public EditionEventsController(EditionEventService editionEventService) {
		this.editionEventService = editionEventService;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@PathVariable String codeSystem) {
		return editionEventService.subscribe(codeSystem);
	}
}
