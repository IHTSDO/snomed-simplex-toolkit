package org.snomed.simplex.client;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchAfterPaginationGuardTest {

	@Test
	void allowsFetchesUpToMaxFetches() {
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(3);

		assertDoesNotThrow(() -> {
			guard.beforeFetch(null);
			guard.afterFullPage(null, "cursorA");
			guard.beforeFetch("cursorA");
			guard.afterFullPage("cursorA", "cursorB");
			guard.beforeFetch("cursorB");
		});
	}

	@Test
	void throwsWhenMaxFetchesExceeded() {
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(2);

		assertDoesNotThrow(() -> {
			guard.beforeFetch(null);
			guard.beforeFetch("cursorA");
		});
		assertThrows(ServiceException.class, () -> guard.beforeFetch("cursorB"));
	}

	@Test
	void throwsWhenRequestCursorRepeats() {
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(10);

		assertDoesNotThrow(() -> {
			guard.beforeFetch(null);
			guard.afterFullPage(null, "cursorA");
			guard.beforeFetch("cursorA");
			guard.afterFullPage("cursorA", "cursorB");
		});
		assertThrows(ServiceException.class, () -> guard.beforeFetch("cursorA"));
	}

	@Test
	void throwsWhenFullPageCursorDoesNotAdvance() throws ServiceException {
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(10);

		guard.beforeFetch("cursorA");
		assertThrows(ServiceException.class, () -> guard.afterFullPage("cursorA", "cursorA"));
	}

	@Test
	void allowsNormalCursorProgression() {
		SearchAfterPaginationGuard guard = new SearchAfterPaginationGuard(10);

		assertDoesNotThrow(() -> {
			guard.beforeFetch("");
			guard.afterFullPage("", "cursorA");
			guard.beforeFetch("cursorA");
			guard.afterFullPage("cursorA", "cursorB");
		});
	}
}
