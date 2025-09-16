package org.snomed.simplex.weblate.domain;

import java.util.List;

public record WeblatePage<T>(int count, String next, String previous, List<T> results) {

	public WeblatePage<T> withoutPagination() {
		return new WeblatePage<>(count, null, null, results);
	}
}
