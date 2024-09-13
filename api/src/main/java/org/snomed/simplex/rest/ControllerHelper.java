package org.snomed.simplex.rest;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class ControllerHelper {

	public static String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}

	public static PageRequest getPageRequest(int offset, int limit) {
		validatePageSize(offset, limit);
		return getPageRequest(offset, limit, null);
	}

	public static PageRequest getPageRequest(int offset, int limit, Sort sort) {
		if (offset % limit != 0) {
			throw new IllegalArgumentException("Offset must be a multiplication of the limit param in order to map to Spring pages.");
		}

		int page = ((offset + limit) / limit) - 1;
		return sort == null ? PageRequest.of(page, limit) : PageRequest.of(page, limit, sort);
	}

	static void validatePageSize(long offset, int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("Limit must be greater than 0.");
		}

		if ((offset + limit) > 10_000) {
			throw new IllegalArgumentException("Maximum unsorted offset + page size is 10,000.");
		}
	}

}
