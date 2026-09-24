package org.snomed.simplex.rest.pojos;

public record DeleteFsnDescriptionsResult(
		int conceptsWithFsn,
		int conceptsUpdated,
		int fsnInactivated,
		int fsnDeleted,
		int fsnSkipped
) {
}
