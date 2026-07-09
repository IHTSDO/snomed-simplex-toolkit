package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import static org.assertj.core.api.Assertions.assertThat;

class SnolateTranslationSetMetadataTest {

	@Test
	void copyForRead_copiesDescription() {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "My set", "my-set",
				"<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-TEST");
		set.setDescription("A test description");

		SnolateTranslationSet copy = set.copyForRead();

		assertThat(copy.getDescription()).isEqualTo("A test description");
		assertThat(copy.getName()).isEqualTo("My set");
	}

	@Test
	void setName_updatesDisplayName() {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "Original", "original",
				"<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-TEST");

		set.setName("Updated title");

		assertThat(set.getName()).isEqualTo("Updated title");
		assertThat(set.getLabel()).isEqualTo("original");
	}

	@Test
	void createSetWithDescription_retainsDescriptionOnEntity() {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "My set", "my-set",
				"<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-TEST");
		set.setDescription("Optional notes about this set");

		assertThat(set.getDescription()).isEqualTo("Optional notes about this set");
		assertThat(set.getName()).isEqualTo("My set");
	}

	@Test
	void setDescription_canBeCleared() {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "My set", "my-set",
				"<<404684003", TranslationSubsetType.ECL, "SNOMEDCT-TEST");
		set.setDescription("Initial");
		set.setDescription(null);

		assertThat(set.getDescription()).isNull();
	}
}
