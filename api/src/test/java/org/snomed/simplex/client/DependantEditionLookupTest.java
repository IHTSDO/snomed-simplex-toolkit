package org.snomed.simplex.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.CodeSystem;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DependantEditionLookupTest {

	private DependantEditionLookup lookup;

	@BeforeEach
	void setUp() {
		lookup = new DependantEditionLookup();
	}

	@Test
	void getParentBranchPath_returnsParentForExtensionBranch() {
		assertEquals("MAIN", DependantEditionLookup.getParentBranchPath("MAIN/SNOMEDCT-BE"));
	}

	@Test
	void getParentBranchPath_returnsNullForRootBranch() {
		assertNull(DependantEditionLookup.getParentBranchPath("MAIN"));
		assertNull(DependantEditionLookup.getParentBranchPath(null));
	}

	@Test
	void resolve_cachesDependantEditionByParentBranch() {
		AtomicInteger fetchCount = new AtomicInteger();
		DependantEditionLookup.DependantEdition edition = new DependantEditionLookup.DependantEdition("International Edition", "SNOMEDCT");

		DependantEditionLookup.DependantEdition first = lookup.resolve("MAIN", branch -> {
			fetchCount.incrementAndGet();
			return edition;
		});
		DependantEditionLookup.DependantEdition second = lookup.resolve("MAIN", branch -> {
			fetchCount.incrementAndGet();
			return edition;
		});

		assertEquals(edition, first);
		assertEquals(edition, second);
		assertEquals(1, fetchCount.get());
	}

	@Test
	void applyDependantEdition_setsFieldsOnCodeSystem() {
		CodeSystem codeSystem = new CodeSystem("Belgian Edition", "SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		DependantEditionLookup.applyDependantEdition(codeSystem,
				new DependantEditionLookup.DependantEdition("International Edition", "SNOMEDCT"));

		assertEquals("International Edition", codeSystem.getDependantEditionName());
		assertEquals("SNOMEDCT", codeSystem.getDependantEditionShortName());
	}
}
