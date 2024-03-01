package org.snomed.simplex.service.validation;

import java.util.*;
import java.util.stream.Collectors;

public class ValidationTriageConfig {

    private Map<String, String> fix = new HashMap<>();
    private Map<String, Set<String>> parsedMap = null;

    public Map<String, String> getFix() {
        return fix;
    }

    public Map<String, Set<String>> getValidationFixMethodToAssertionIdMap() {
        if (parsedMap == null) {
            Map<String, Set<String>> map = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : fix.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                // Example: validation.fix.automatic.set-description-case-sensitive.assertionIds=d007641a-a124-4096-84fe-d2e09dcb7f40,eb7eaf62-d900-4199-beec-a843f657dfa3
                key = key.replace(".assertionIds", "");
                key = key.replace(".", "-fix.");
                map.put(key, Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toSet()));
            }
            parsedMap = map;
        }
        return parsedMap;
    }
}
