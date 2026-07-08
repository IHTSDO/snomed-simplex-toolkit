package org.snomed.simplex.client;

import org.springframework.boot.info.BuildProperties;

class SnowstormUserAgentSupport {

	private static final String VERSION_PLACEHOLDER = "{version}";
	private static final String DEVELOPMENT_VERSION = "development";

	private SnowstormUserAgentSupport() {
	}

	static String resolve(String userAgentTemplate, BuildProperties buildProperties) {
		if (userAgentTemplate == null || userAgentTemplate.isBlank()) {
			return null;
		}
		String version = buildProperties != null && buildProperties.getVersion() != null
				? buildProperties.getVersion()
				: DEVELOPMENT_VERSION;
		return userAgentTemplate.replace(VERSION_PLACEHOLDER, version);
	}

}
