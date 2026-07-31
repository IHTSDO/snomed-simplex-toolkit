package org.snomed.simplex.client;

import org.snomed.simplex.exceptions.ServiceException;

import java.util.HashSet;
import java.util.Set;

/**
 * Defensive limits for Snowstorm searchAfter pagination loops.
 */
public class SearchAfterPaginationGuard {

	private final int maxFetches;
	private int fetchCount;
	private final Set<String> seenRequestCursors = new HashSet<>();

	public SearchAfterPaginationGuard(int maxFetches) {
		this.maxFetches = maxFetches;
	}

	public void beforeFetch(String requestSearchAfter) throws ServiceException {
		fetchCount++;
		if (fetchCount > maxFetches) {
			throw new ServiceException("Snowstorm searchAfter pagination aborted after %d fetches (limit %d).".formatted(fetchCount - 1, maxFetches));
		}
		String cursor = normalizeCursor(requestSearchAfter);
		if (!cursor.isEmpty() && !seenRequestCursors.add(cursor)) {
			throw new ServiceException("Snowstorm searchAfter pagination loop detected: repeated request cursor '%s' on fetch %d.".formatted(cursor, fetchCount));
		}
	}

	public void afterFullPage(String requestSearchAfter, String responseSearchAfter) throws ServiceException {
		String request = normalizeCursor(requestSearchAfter);
		String response = normalizeCursor(responseSearchAfter);
		if (!response.isEmpty() && response.equals(request)) {
			throw new ServiceException("Snowstorm searchAfter cursor did not advance on fetch %d (cursor '%s').".formatted(fetchCount, response));
		}
	}

	private static String normalizeCursor(String cursor) {
		return cursor == null ? "" : cursor;
	}
}
