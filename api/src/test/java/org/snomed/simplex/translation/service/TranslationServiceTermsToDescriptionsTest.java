package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.Description;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.snomed.simplex.client.domain.Description.Type.SYNONYM;

class TranslationServiceTermsToDescriptionsTest {

	private static final String LANG = "fr";
	private static final String REFSET = "123100000100";

	@Test
	void termsToDescriptions_firstTermIsPreferred_restAreAcceptable() {
		Map<Long, List<Description>> descriptions = TranslationService.termsToDescriptions(
				Map.of(100L, List.of("preferred", "synonym")), LANG, REFSET);

		List<Description> conceptDescriptions = descriptions.get(100L);
		assertThat(conceptDescriptions).hasSize(2);
		assertThat(conceptDescriptions.get(0).getTerm()).isEqualTo("preferred");
		assertThat(conceptDescriptions.get(0).getType()).isEqualTo(SYNONYM);
		assertThat(conceptDescriptions.get(0).getAcceptabilityMap()).containsEntry(REFSET, Description.Acceptability.PREFERRED);
		assertThat(conceptDescriptions.get(1).getTerm()).isEqualTo("synonym");
		assertThat(conceptDescriptions.get(1).getAcceptabilityMap()).containsEntry(REFSET, Description.Acceptability.ACCEPTABLE);
	}

	@Test
	void termsToDescriptions_skipsBlankTerms() {
		Map<Long, List<Description>> descriptions = TranslationService.termsToDescriptions(
				Map.of(100L, List.of("preferred", "", "synonym")), LANG, REFSET);

		assertThat(descriptions.get(100L)).hasSize(2);
		assertThat(descriptions.get(100L).get(1).getTerm()).isEqualTo("synonym");
	}

}
