package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.Description;

public final class DescriptionRemoval {

	public enum Outcome {
		INACTIVATED, DELETED, UNCHANGED
	}

	private DescriptionRemoval() {
	}

	public static Outcome markForRemoval(Description description) {
		if (!description.isActive()) {
			return Outcome.UNCHANGED;
		}
		if (description.isReleased()) {
			description.setActive(false);
			description.setRemove(false);
			if (description.getAcceptabilityMap() != null) {
				description.getAcceptabilityMap().clear();
			}
			return Outcome.INACTIVATED;
		}
		description.setRemove(true);
		description.setActive(false);
		return Outcome.DELETED;
	}
}
