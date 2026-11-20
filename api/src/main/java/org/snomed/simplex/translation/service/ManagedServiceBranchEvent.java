package org.snomed.simplex.translation.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A Managed Service (SI Authoring Platform) translation branch event.
 * <p>
 * Parsed from the JSON message published to the branch events destination: a list of
 * {@code {"event": "REBASE|PROMOTE|DELETE", "branch": {"branchPath", "sourceBranchPath"?, "labels"}}}.
 * A single (unwrapped) object is also accepted, and so is the flat form with a {@code commitType} of
 * {@code PROMOTION} or {@code REBASE} and no {@code event} wrapper.
 *
 * @param type             what happened
 * @param branchPath       the branch that was updated: the project branch for {@link Type#REBASE} and {@link Type#PROMOTE},
 *                         the task branch for {@link Type#DELETE}
 * @param sourceBranchPath where the change came from: the task branch for a promotion, the parent for a rebase (may be null)
 */
public record ManagedServiceBranchEvent(Type type, String branchPath, String sourceBranchPath) {

	public static final String TRANSLATION_LABEL = "translation";

	private static final String BRANCH_FIELD = "branch";

	public enum Type {
		REBASE, PROMOTE, DELETE
	}

	/** Events in the message that are for translation branches and of a known type; anything else is dropped. */
	public static List<ManagedServiceBranchEvent> parse(JsonNode message) {
		List<ManagedServiceBranchEvent> events = new ArrayList<>();
		if (message == null) {
			return events;
		}
		if (message.isArray()) {
			message.forEach(node -> parseOne(node).ifPresent(events::add));
		} else {
			parseOne(message).ifPresent(events::add);
		}
		return events;
	}

	private static java.util.Optional<ManagedServiceBranchEvent> parseOne(JsonNode node) {
		JsonNode wrapped = node.get(BRANCH_FIELD);
		JsonNode branch = wrapped != null && wrapped.isObject() ? wrapped : node;
		String eventName = text(node, "event");
		if (eventName == null) {
			eventName = switch (String.valueOf(text(branch, "commitType"))) {
				case "PROMOTION" -> "PROMOTE";
				case "REBASE" -> "REBASE";
				default -> null;
			};
		}
		Type type = null;
		if (eventName != null) {
			for (Type candidate : Type.values()) {
				if (candidate.name().equalsIgnoreCase(eventName)) {
					type = candidate;
				}
			}
		}
		String branchPath = text(branch, "branchPath");
		String labels = text(branch, "labels");
		if (type == null || branchPath == null || labels == null || !labels.contains(TRANSLATION_LABEL)) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(new ManagedServiceBranchEvent(type, branchPath, text(branch, "sourceBranchPath")));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}
}
