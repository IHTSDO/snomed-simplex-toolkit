package org.snomed.simplex.rest.pojos;

import java.util.List;

public record TranslationUnitPage<T>(int count, String next, String previous, List<T> results) {

	public TranslationUnitPage<T> withoutPagination() {
		return new TranslationUnitPage<>(count, null, null, results);
	}
}
