package com.snomed.derivativemanagementtool.domain;

import java.util.Properties;

/**
 * Configuration for a set of products within one code system
 */
public class CodeSystemProperties {

	public static final String SNOWSTORM_URL = "snowstorm.url";

	private String snowstormUrl;

	public CodeSystemProperties() {
	}

	public CodeSystemProperties(Properties properties) {
		snowstormUrl = properties.getProperty(SNOWSTORM_URL);
	}

	public Properties createProperties() {
		Properties properties = new Properties();
		if (snowstormUrl != null) {
			properties.put(SNOWSTORM_URL, snowstormUrl);
		}
		return properties;
	}

	public String getSnowstormUrl() {
		return snowstormUrl;
	}

	public void setSnowstormUrl(String snowstormUrl) {
		this.snowstormUrl = snowstormUrl;
	}
}
