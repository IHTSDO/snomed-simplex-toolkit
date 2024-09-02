package org.snomed.simplex.service.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ValidationServiceTest {

	@Autowired
	private ValidationService validationService;

	private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
			.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

	@Test
	void test() throws IOException {
		ValidationReport validationReport = objectMapper.readValue(getClass().getResourceAsStream("/rvf-report-for-fix-list.json"), ValidationReport.class);
		ValidationFixList validationFixList = validationService.getValidationFixList(validationReport);
		assertEquals(21, validationFixList.errorCount());
		assertEquals(15, validationFixList.warningCount());
		List<ValidationFix> fixes = validationFixList.fixes();
		assertEquals(5, fixes.size());
		assertEquals("[automatic-fix - set-description-case-sensitive - WARNING, " +
						"user-fix - edit-or-remove-duplicate-term-different-concepts - ERROR, " +
						"user-fix - term-incorrect-case - WARNING, " +
						"user-fix - update-term - ERROR, " +
						"unknown-fix - unknown - ERROR]",
				fixes.stream().map(fix -> "%s - %s - %s".formatted(fix.getType(), fix.getSubtype(), fix.getSeverity())).toList().toString());

		ValidationFix duplicateTermsFix = fixes.get(0);
		assertEquals(4, duplicateTermsFix.getComponentCount());
		Set<String> componentIds = new HashSet<>();
		for (FixComponent component : duplicateTermsFix.getComponents()) {
			System.out.println(component.assertionText());
			assertTrue(componentIds.add(component.componentId()), () -> String.format("Component id %s is not unique", component.componentId()));
		}
	}

}
