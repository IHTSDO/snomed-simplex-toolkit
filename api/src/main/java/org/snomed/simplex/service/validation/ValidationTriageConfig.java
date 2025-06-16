package org.snomed.simplex.service.validation;

import org.apache.commons.lang3.tuple.Pair;
import org.snomed.simplex.exceptions.ServiceException;

import java.util.*;

import static java.lang.String.format;

public class ValidationTriageConfig {

	private final Map<String, String> fix = new HashMap<>();
	private Map<String, List<String>> parsedFixMap = null;
	private Map<String, Pair<String, String>> parsedTitleMap = null;

	public Map<String, String> getFix() {
		return fix;
	}

	public Map<String, List<String>> getValidationFixMethodToAssertionIdMap() throws ServiceException {
		if (parsedFixMap == null) {
			init();
		}
		return parsedFixMap;
	}

	public Map<String, Pair<String, String>> getValidationFixMethodToTitleAndInstructionsMap() throws ServiceException {
		if (parsedTitleMap == null) {
			init();
		}
		return parsedTitleMap;
	}

	private synchronized void init() throws ServiceException {
		if (parsedFixMap == null) {
			Map<String, List<String>> fixMap = new LinkedHashMap<>();
			Map<String, Pair<String, String>> titleMap = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : fix.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (key.endsWith("assertionIds")) {
					// Example: validation.fix.automatic.set-description-case-sensitive.assertionIds=d007641a-a124-4096-84fe-d2e09dcb7f40,eb7eaf62-d900-4199-beec-a843f657dfa3
					key = key.replace(".assertionIds", "");
					key = key.replace(".", "-fix.");
					fixMap.put(key, Arrays.stream(value.split(",")).map(String::trim).toList());
				} else if (key.endsWith("titleAndInstructions")) {
					key = key.replace(".titleAndInstructions", "");
					key = key.replace(".", "-fix.");
					String[] split = value.split("\\|");
					if (split.length != 2) {
						throw new ServiceException(format("Misconfigured validation.fix configuration. " +
								"Expecting value of %s to have format xxx|xxx but found %s pipe separated values.", entry.getKey(), split.length));
					}
					titleMap.put(key, Pair.of(split[0].trim(), split[1].trim()));
				}
			}
			parsedFixMap = fixMap;
			parsedTitleMap = titleMap;
		}
	}
}
