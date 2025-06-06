package org.snomed.simplex.service.validation;

import org.apache.commons.lang3.tuple.Pair;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ValidationService {

	public static final String UNKNOWN_FIX_KEY = "unknown-fix.unknown";

	private final ValidationServiceClient validationServiceClient;
	private final SnowstormClientFactory snowstormClientFactory;
	private final SupportRegister supportRegister;

	private final Map<String, List<String>> validationFixMethodToAssertionIdMap;
	private final Map<String, Pair<String, String>> validationFixMethodToTitleAndInstructionsMap;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Map<String, Integer> assertionSortOrderMap;
	private final Set<String> missingFixesReported = new HashSet<>();

	public ValidationService(ValidationTriageConfig validationTriageConfig, ValidationServiceClient validationServiceClient,
			SnowstormClientFactory snowstormClientFactory, SupportRegister supportRegister) throws ServiceException {

		validationFixMethodToAssertionIdMap = validationTriageConfig.getValidationFixMethodToAssertionIdMap();
		validationFixMethodToTitleAndInstructionsMap = validationTriageConfig.getValidationFixMethodToTitleAndInstructionsMap();
		this.validationServiceClient = validationServiceClient;
		this.snowstormClientFactory = snowstormClientFactory;
		this.supportRegister = supportRegister;

		assertionSortOrderMap = new HashMap<>();
		int i = 1;
		for (List<String> assertionIds : validationFixMethodToAssertionIdMap.values()) {
			for (String assertionId : assertionIds) {
				assertionSortOrderMap.put(assertionId, i++);
			}
		}
	}

	public ValidationFixList getValidationFixList(ValidationReport validationReport) {
		return createFixList(validationReport);
	}

	private ValidationFixList createFixList(ValidationReport validationReport) {
		ValidationReport.TestResult testResult = validationReport.rvfValidationResult().TestResult();
		Map<String, ValidationFix> fixesRequired = new HashMap<>();
		buildValidationFixMap(testResult.assertionsFailed(), fixesRequired, Severity.ERROR);
		int errorCount = fixesRequired.values().stream().mapToInt(ValidationFix::getComponentCount).sum();
		buildValidationFixMap(testResult.assertionsWarning(), fixesRequired, Severity.WARNING);
		int warningCount = fixesRequired.values().stream().mapToInt(ValidationFix::getComponentCount).sum();
		warningCount = warningCount - errorCount;

		// Create fix list with the same order as the config map
		List<ValidationFix> fixList = new ArrayList<>();
		List<String> fixKeys = new ArrayList<>(validationFixMethodToAssertionIdMap.keySet());
		// Sort alphabetically
		fixKeys.sort(null);
		// Move unknown-fix to the end
		fixKeys.remove(UNKNOWN_FIX_KEY);
		fixKeys.add(UNKNOWN_FIX_KEY);
		for (String fixId : fixKeys) {
			ValidationFix validationFix = fixesRequired.get(fixId);
			if (validationFix != null) {
				fixList.add(validationFix);
			}
		}

		return new ValidationFixList(errorCount, warningCount, fixList);
	}

	private void buildValidationFixMap(List<ValidationReport.Assertion> assertions, Map<String, ValidationFix> fixesRequired, Severity severity) {

		// Sort assertions by order in validation fix map - this gives prioritisation when deduplicating component issues
		assertions.sort(Comparator.comparingInt(assertion -> assertionSortOrderMap.getOrDefault(assertion.assertionUuid(), Integer.MAX_VALUE)));

		for (ValidationReport.Assertion assertion : assertions) {
			if (assertion.firstNInstances() == null) {
				continue;
			}
			String assertionUuid = assertion.assertionUuid();
			boolean fixFound = false;
			for (Map.Entry<String, List<String>> fixToAssertionIds : validationFixMethodToAssertionIdMap.entrySet()) {
				if (fixToAssertionIds.getValue().contains(assertionUuid)) {
					String fixId = fixToAssertionIds.getKey();
					createFix(fixesRequired, assertion, fixId, severity);
					fixFound = true;
					break;
				}
			}
			if (!fixFound) {
				if (missingFixesReported.add(assertionUuid)) {
					logger.error("No validation fix found for assertion {}", assertionUuid);
				}
				createFix(fixesRequired, assertion, UNKNOWN_FIX_KEY, severity);
			}
		}
	}

	private void createFix(Map<String, ValidationFix> fixesRequired, ValidationReport.Assertion assertion, String fixId, Severity severity) {
		Pair<String, String> titleAndInstructions =
				validationFixMethodToTitleAndInstructionsMap.getOrDefault(fixId, Pair.of("Unknown fix type", "Unknown fix type"));
		ValidationFix validationFix = fixesRequired.computeIfAbsent(fixId, i ->
				new ValidationFix(fixId, titleAndInstructions.getLeft(), titleAndInstructions.getRight(), severity));
		for (ValidationReport.AssertionIssue issueInstance : assertion.firstNInstances()) {
			// Components are only added if new componentId is new in the set
			validationFix.addComponent(new FixComponent(issueInstance.conceptId(), issueInstance.componentId(), assertion.assertionText()));
		}
	}

	public void runAutomaticFixes(CodeSystem codeSystem) throws ServiceException {
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
			logger.info("Processing {} automatic fixes for codesystem {} : {}", automaticFixes.size(), codeSystem,
					automaticFixes.stream().map(ValidationFix::getSubtype).toList());

			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			CodeSystem codeSystemObject = snowstormClient.getCodeSystemOrThrow(codeSystem);

			for (ValidationFix automaticFix : automaticFixes) {
				String subtype = automaticFix.getSubtype();
				if ("set-description-case-sensitive".equals(subtype)) {
					processAutomaticFixCaseSensitive(automaticFix, snowstormClient, codeSystemObject);
				} else if ("remove-fsn".equals(subtype)) {
					processAutomaticFixRemoveFSN(automaticFix, snowstormClient, codeSystemObject);
				} else {
					supportRegister.handleSystemError(job, String.format("Unrecognised automatic fix type '%s'.", subtype));
				}
			}
			logger.info("Completed processing {} automatic fixes for codesystem {}.", automaticFixes.size(), codeSystem);
		}
	}

	private static void processAutomaticFixCaseSensitive(ValidationFix automaticFix, SnowstormClient snowstormClient, CodeSystem codeSystemObject) throws ServiceException {
		Map<Long, Set<String>> descriptionsToSetCaseSensitive = new HashMap<>();
		for (FixComponent component : automaticFix.getComponents()) {
			descriptionsToSetCaseSensitive.computeIfAbsent(Long.parseLong(component.conceptId()), k -> new HashSet<>())
					.add(component.componentId());
		}
		if (!descriptionsToSetCaseSensitive.isEmpty()) {
			snowstormClient.bulkChangeDescriptions(descriptionsToSetCaseSensitive,
					codeSystemObject, description -> {
						if (description.getCaseSignificance() != Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) {
							description.setCaseSignificance(Description.CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
							return true;
						}
						return false;
					}, "Fixing case sensitivity of descriptions");
		}
	}

	private static void processAutomaticFixRemoveFSN(ValidationFix automaticFix, SnowstormClient snowstormClient, CodeSystem codeSystemObject) throws ServiceException {
		Map<Long, Set<String>> descriptionsToRemove = new HashMap<>();
		for (FixComponent component : automaticFix.getComponents()) {
			descriptionsToRemove.computeIfAbsent(Long.parseLong(component.conceptId()), k -> new HashSet<>())
					.add(component.componentId());
		}
		if (!descriptionsToRemove.isEmpty()) {
			snowstormClient.bulkChangeDescriptions(descriptionsToRemove,
					codeSystemObject, description -> {
						description.setRemove(true);
						return true;
					}, "Removing redundant FSN descriptions");
		}
	}
}
