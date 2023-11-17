package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CodeSystemServiceTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@MockBean
	private SnowstormClientFactory snowstormClientFactory;

	@Test
	void createCodeSystem() {
//		codeSystemService.createCodeSystem();
	}
}