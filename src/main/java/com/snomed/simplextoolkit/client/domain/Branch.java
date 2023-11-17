package com.snomed.simplextoolkit.client.domain;

import java.util.Map;

public class Branch {

	public static final String DEFAULT_NAMESPACE_METADATA_KEY = "defaultNamespace";
	public static final String SIMPLEX_WORKING_BRANCH_METADATA_KEY = "simplex.workingBranch";
	public static final String CLASSIFIED_METADATA_KEY = "internal.classified";

	private String path;
	private Map<String, Object> metadata;

	public String getPath() {
		return path;
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
