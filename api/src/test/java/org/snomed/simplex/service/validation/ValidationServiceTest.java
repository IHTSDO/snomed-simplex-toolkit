package org.snomed.simplex.service.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ValidationServiceTest {

	@Autowired
	private ValidationService validationService;

	private ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
			.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

	@Test
	public void test() throws IOException {
		ValidationReport validationReport = objectMapper.readValue(getClass().getResourceAsStream("/rvf-report-for-fix-list.json"), ValidationReport.class);
		ValidationFixList validationFixList = validationService.getValidationFixList(validationReport);
		assertEquals(16, validationFixList.errorCount());
		assertEquals(4, validationFixList.warningCount());
		List<ValidationFix> fixes = validationFixList.fixes();
		assertEquals(3, fixes.size());
		assertEquals("[user-fix - edit-or-remove-duplicate-term-different-concepts, " +
				"user-fix - update-term, " +
				"automatic-fix - set-description-case-sensitive]",
				fixes.stream().map(fix -> String.format("%s - %s", fix.getType(), fix.getSubtype())).toList().toString());

		ValidationFix duplicateTermsFix = fixes.get(0);
		assertEquals(5, duplicateTermsFix.getComponentCount());
	}

}
