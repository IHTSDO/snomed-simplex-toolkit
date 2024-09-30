package org.snomed.simplex.client.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum EditionStatus {

	MAINTENANCE("Maintenance Mode"),
	AUTHORING("Authoring"),
	PREPARING_RELEASE("Preparing Release"),
	RELEASE("Release"),
	PUBLISHING("Publishing");

	private static Set<String> names;

	private final String display;

	EditionStatus(String display) {
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}

	public static Set<String> getNames() {
		if (names == null) {
			names = Arrays.stream(EditionStatus.values()).map(EditionStatus::name).collect(Collectors.toSet());
		}
		return names;
	}
}
