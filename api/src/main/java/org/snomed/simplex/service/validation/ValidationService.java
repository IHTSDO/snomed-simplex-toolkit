package org.snomed.simplex.service.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.AsyncJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ValidationService {

	@Autowired
	private ValidationServiceClient validationServiceClient;

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@Autowired
	private SupportRegister supportRegister;

	private final Map<String, Set<String>> validationFixMethodToAssertionIdMap;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValidationService(@Autowired ValidationTriageConfig validationTriageConfig) {
		validationFixMethodToAssertionIdMap = validationTriageConfig.getValidationFixMethodToAssertionIdMap();
	}

	public ValidationFixList getValidationFixList(ValidationReport validationReport) {
		return createFixList(validationReport);
	}

	private ValidationFixList createFixList(ValidationReport validationReport) {
		ValidationReport.TestResult testResult = validationReport.rvfValidationResult().TestResult();
		HashMap<String, ValidationFix> fixesRequired = new HashMap<>();
		buildValidationFixMap(testResult.assertionsFailed(), fixesRequired);
		int errorCount = fixesRequired.values().stream().mapToInt(ValidationFix::getComponentCount).sum();
		buildValidationFixMap(testResult.assertionsWarning(), fixesRequired);
		int warningCount = fixesRequired.values().stream().mapToInt(ValidationFix::getComponentCount).sum();
		warningCount = warningCount - errorCount;

		// Create fix list with the same order as the config map
		List<ValidationFix> fixList = new ArrayList<>();
		for (String fixId : validationFixMethodToAssertionIdMap.keySet()) {
			ValidationFix validationFix = fixesRequired.get(fixId);
			if (validationFix != null) {
				fixList.add(validationFix);
			}
		}

		return new ValidationFixList(errorCount, warningCount, fixList);
	}

	private void buildValidationFixMap(List<ValidationReport.Assertion> assertions, HashMap<String, ValidationFix> fixesRequired) {
		for (ValidationReport.Assertion assertion : assertions) {
			if (assertion.firstNInstances() == null) {
				continue;
			}
			String assertionUuid = assertion.assertionUuid();
			boolean fixFound = false;
			for (Map.Entry<String, Set<String>> fixToAssertionIds : validationFixMethodToAssertionIdMap.entrySet()) {
				if (fixToAssertionIds.getValue().contains(assertionUuid)) {
					String fixId = fixToAssertionIds.getKey();
					ValidationFix validationFix = fixesRequired.computeIfAbsent(fixId, i -> new ValidationFix(fixId));
					for (ValidationReport.AssertionIssue issueInstance : assertion.firstNInstances()) {
						validationFix.addComponent(
								new FixComponent(issueInstance.conceptId(), issueInstance.componentId(),
										assertion.assertionText()));
					}
					fixFound = true;
					break;
				}
			}
			if (!fixFound) {
				logger.info("No validation fix found for assertion {}", assertionUuid);
			}
		}
	}

	// Admin function
	public void reprocessAutomaticFixes(CodeSystem codeSystem) throws ServiceException {
		String reportUrl = codeSystem.getLatestValidationReport();
		if (reportUrl == null) {
			throw new ServiceExceptionWithStatusCode("There is no validation report.", HttpStatus.CONFLICT);
		}
		ValidationReport validation = validationServiceClient.getValidation(reportUrl);
		processAutomaticFixes(null, validation, codeSystem.getShortName());
	}

	public void processAutomaticFixes(AsyncJob job, ValidationReport validationReport, String codeSystem) throws ServiceException {
		ValidationFixList validationFixList = getValidationFixList(validationReport);
		List<ValidationFix> automaticFixes = validationFixList.fixes().stream().filter(ValidationFix::isAutomatic).toList();
		if (!automaticFixes.isEmpty()) {
			logger.info("Processing {} automatic fixes for codesystem {}.", automaticFixes.size(), codeSystem);
			Map<Long, Set<String>> descriptionsToSetCaseSensitive = new HashMap<>();
			for (ValidationFix automaticFix : automaticFixes) {
				String subtype = automaticFix.getSubtype();
				if ("set-description-case-sensitive".equals(subtype)) {
					for (FixComponent component : automaticFix.getComponents()) {
						descriptionsToSetCaseSensitive.computeIfAbsent(Long.parseLong(component.getConceptId()), k -> new HashSet<>())
								.add(component.getComponentId());
					}
				} else {
					supportRegister.handleSystemError(job, String.format("Unrecognised automatic fix type '%s'.", subtype));
				}
			}
			if (!descriptionsToSetCaseSensitive.isEmpty()) {
				SnowstormClient snowstormClient = snowstormClientFactory.getClient();
				CodeSystem codeSystemObject = snowstormClient.getCodeSystemOrThrow(codeSystem);
				snowstormClient.bulkSetDescriptionCaseSensitivity(descriptionsToSetCaseSensitive,
						Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, codeSystemObject);
			}
			logger.info("Completed processing {} automatic fixes for codesystem {}.", automaticFixes.size(), codeSystem);
		}
	}
}
