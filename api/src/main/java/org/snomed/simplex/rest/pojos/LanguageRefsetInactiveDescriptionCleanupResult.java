package org.snomed.simplex.rest.pojos;

public record LanguageRefsetInactiveDescriptionCleanupResult(
		int activeMembersScanned,
		int membersInactivated,
		int activeMembersRemaining
) {
}
