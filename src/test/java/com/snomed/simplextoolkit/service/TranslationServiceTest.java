package com.snomed.simplextoolkit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslationServiceTest {

	@Test
	void guessCaseSignificance() {
		TranslationService service = new TranslationService();
		assertEquals("ENTIRE_TERM_CASE_SENSITIVE", service.guessCaseSignificance("SNOMED CT core module (core metadata concept)", true));
		assertEquals("ENTIRE_TERM_CASE_SENSITIVE", service.guessCaseSignificance("sinh thiết chọc hút bằng kim nhỏ nang giả tụy có hướng dẫn CT", false));
		assertEquals("CASE_INSENSITIVE", service.guessCaseSignificance("sinh thiết chọc hút bằng kim nhỏ nang giả tụy có hướng dẫn", false));
		assertEquals("CASE_INSENSITIVE", service.guessCaseSignificance("Clinical finding (finding)", true));
		assertEquals("ENTIRE_TERM_CASE_SENSITIVE", service.guessCaseSignificance("Clinical finding (finding)", false));
	}
}
