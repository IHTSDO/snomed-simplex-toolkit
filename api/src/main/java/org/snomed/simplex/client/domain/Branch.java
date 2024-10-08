package org.snomed.simplex.client.domain;

import java.util.Map;
import java.util.Set;

public class Branch {

	public static final String DEFAULT_NAMESPACE_METADATA_KEY = "defaultNamespace";
	public static final String SIMPLEX_WORKING_BRANCH_METADATA_KEY = "simplex.workingBranch";
	public static final String CLASSIFIED_METADATA_KEY = "internal.classified";
	public static final String DEPENDENCY_PACKAGE_METADATA_KEY = "dependencyPackage";
	public static final String PREVIOUS_PACKAGE_METADATA_KEY = "previousPackage";
	public static final String PREVIOUS_DEPENDENCY_PACKAGE_METADATA_KEY = "previousDependencyPackage";
	public static final String LATEST_VALIDATION_REPORT_METADATA_KEY = "latestValidationReport";
	public static final String LATEST_BUILD_METADATA_KEY = "latestBuild";
	public static final String BUILD_STATUS_METADATA_KEY = "buildStatus";
	public static final String SHOW_CUSTOM_CONCEPTS = "showCustomConcepts";
	public static final String DEFAULT_MODULE_ID_METADATA_KEY = "defaultModuleId";
	public static final String EDITION_STATUS_METADATA_KEY = "editionStatus";
	public static final String SIMPLEX_TRANSLATION_METADATA_KEY = "simplex.translation.";

	public static final String ORGANISATION_NAME = "package.orgName";
	public static final String ORGANISATION_CONTACT_DETAILS = "package.orgContactDetails";

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
