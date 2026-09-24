package org.snomed.simplex.client;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.Description;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptionRemovalTest {

	@Test
	void markForRemoval_hardDeletesUnreleasedDescription() {
		Description description = fsn("1", true, false);
		DescriptionRemoval.Outcome outcome = DescriptionRemoval.markForRemoval(description);

		assertEquals(DescriptionRemoval.Outcome.DELETED, outcome);
		assertTrue(description.isRemove());
		assertFalse(description.isActive());
	}

	@Test
	void markForRemoval_inactivatesReleasedDescriptionAndClearsAcceptability() {
		Description description = fsn("1", true, true);
		description.addAcceptability(Concepts.US_LANG_REFSET, Description.Acceptability.PREFERRED);

		DescriptionRemoval.Outcome outcome = DescriptionRemoval.markForRemoval(description);

		assertEquals(DescriptionRemoval.Outcome.INACTIVATED, outcome);
		assertFalse(description.isRemove());
		assertFalse(description.isActive());
		assertTrue(description.getAcceptabilityMap().isEmpty());
	}

	@Test
	void markForRemoval_skipsAlreadyInactiveDescription() {
		Description description = fsn("1", false, false);

		DescriptionRemoval.Outcome outcome = DescriptionRemoval.markForRemoval(description);

		assertEquals(DescriptionRemoval.Outcome.UNCHANGED, outcome);
	}

	private static Description fsn(String id, boolean active, boolean released) {
		Description description = new Description(Description.Type.FSN, "en", "Finding (finding)",
				Description.CaseSignificance.CASE_INSENSITIVE, Map.of(Concepts.US_LANG_REFSET, Description.Acceptability.PREFERRED));
		description.setDescriptionId(id);
		description.setActive(active);
		description.setReleased(released);
		description.setModuleId("101000003010");
		return description;
	}
}
