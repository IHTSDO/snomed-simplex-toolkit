package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetServiceTest {

	@Test
	void fixConceptCode() throws ServiceException {
		assertEquals("165477451000003105", SpreadsheetService.fixConceptCode("1.65477451000003E+017", 1, 1));
		assertEquals("195967001", SpreadsheetService.fixConceptCode("1.95967001E8", 1, 1));
	}
}
