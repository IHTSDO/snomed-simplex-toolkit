package com.snomed.simplextoolkit.domain;

import java.util.Map;

public class Branch {

	private String path;
	private Map<String, Object> metadata;

	public String getPath() {
		return path;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public String getDefaultModule() {
		if (metadata != null) {
			Object defaultModule = metadata.get("defaultModuleId");
			if (defaultModule instanceof String) {
				return (String) defaultModule;
			}
		}
		return null;
	}
}
