package org.snomed.simplex.service.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.rvf.ValidationReport;
import org.snomed.simplex.client.rvf.ValidationServiceClient;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ValidationService {

    @Autowired
    private ValidationServiceClient validationServiceClient;

    private final Map<String, Set<String>> validationFixMethodToAssertionIdMap;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ValidationService(@Autowired ValidationTriageConfig validationTriageConfig) {
        validationFixMethodToAssertionIdMap = validationTriageConfig.getValidationFixMethodToAssertionIdMap();
    }

    public ValidationFixList getValidationFixList(String codeSystem, ValidationReport validationReport) {
        return createFixList(validationReport, codeSystem);
    }

    private ValidationFixList createFixList(ValidationReport validationReport, String codeSystem) {
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
}
