package org.snomed.simplex.client.domain;

import java.util.Map;
import java.util.Set;

public class Branch {

	public static final String DEFAULT_NAMESPACE_METADATA_KEY = "defaultNamespace";
	public static final String SIMPLEX_WORKING_BRANCH_METADATA_KEY = "simplex.workingBranch";
	public static final String CLASSIFIED_METADATA_KEY = "internal.classified";
	public static final String DEPENDENCY_PACKAGE_METADATA_KEY = "dependencyPackage";
	public static final String LATEST_VALIDATION_REPORT_METADATA_KEY = "latestValidationReport";
	public static final String SHOW_CUSTOM_CONCEPTS = "showCustomConcepts";

	private String path;
	private Long headTimestamp;
	private Map<String, Object> metadata;
	private Set<String> userRoles;

	public String getPath() {
		return path;
	}

	public Long getHeadTimestamp() {
		return headTimestamp;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public String getDefaultModule() {
		String key = "defaultModuleId";
		return getMetadataValue(key);
	}

	public String getMetadataValue(String key) {
		if (metadata != null) {
			if (key.startsWith("internal.")) {
				String keyPart = key.substring("internal.".length());
				Object thing = metadata.get("internal");
				if (thing instanceof Map) {
					Map<?, ?> map = (Map<?, ?>) thing;
					return (String) map.get(keyPart);
				}
			} else {
				Object defaultModule = metadata.get(key);
				if (defaultModule instanceof String) {
					return (String) defaultModule;
				}
			}
		}
		return null;
	}

}
