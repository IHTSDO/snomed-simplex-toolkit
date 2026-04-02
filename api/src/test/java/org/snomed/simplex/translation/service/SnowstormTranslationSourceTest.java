package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.rf2.Rf2Term;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowstormTranslationSourceTest {

	@Test
	void getTranslationState() {
		SnowstormTranslationSource source = new SnowstormTranslationSource(null, null, "fr", "123100000100");
		HashMap<Long, Rf2Term> terms = new HashMap<>();
		terms.put(101L, new Rf2Term("Ccc").addAcceptability(123100000100L, parseLong(Concepts.ACCEPTABLE)));
		terms.put(102L, new Rf2Term("Bbb").addAcceptability(123100000100L, parseLong(Concepts.PREFERRED)));
		terms.put(103L, new Rf2Term("Aaa").addAcceptability(123100000100L, parseLong(Concepts.ACCEPTABLE)));

		TranslationState translationState = source.getTranslationState(Map.of(123L, terms));

		List<String> strings = translationState.getConceptTerms().get(123L);
		assertEquals(3, strings.size());
		assertEquals("Bbb", strings.get(0));
		assertEquals("Aaa", strings.get(1));
		assertEquals("Ccc", strings.get(2));
	}
}
