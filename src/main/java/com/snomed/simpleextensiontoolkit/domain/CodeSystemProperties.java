package com.snomed.simpleextensiontoolkit.domain;

import java.util.Properties;

/**
 * Configuration for a set of products within one code system
 */
public class CodeSystemProperties {

	public static final String SNOWSTORM_URL = "snowstorm.url";
	public static final String CODESYSTEM = "codesystem";
	public static final String DEFAULT_MODULE = "codesystem.defaultModule";

	private String snowstormUrl;
	private String codesystem;
	private String defaultModule;

	public CodeSystemProperties() {
	}

	public CodeSystemProperties(Properties properties) {
		snowstormUrl = properties.getProperty(SNOWSTORM_URL);
		codesystem = properties.getProperty(CODESYSTEM);
		defaultModule = properties.getProperty(DEFAULT_MODULE);
	}

	public Properties createProperties() {
		Properties properties = new Properties();
		if (snowstormUrl != null) {
			properties.put(SNOWSTORM_URL, snowstormUrl);
		}
		if (codesystem != null) {
			properties.put(CODESYSTEM, codesystem);
		}
		if (defaultModule != null) {
			properties.put(DEFAULT_MODULE, defaultModule);
		}
		return properties;
	}

	public String getSnowstormUrl() {
		return snowstormUrl;
	}

	public void setSnowstormUrl(String snowstormUrl) {
		this.snowstormUrl = snowstormUrl;
	}

	public String getCodesystem() {
		return codesystem;
	}

	public void setCodesystem(String codesystem) {
		this.codesystem = codesystem;
	}

	public String getDefaultModule() {
		return defaultModule;
	}

	public void setDefaultModule(String defaultModule) {
		this.defaultModule = defaultModule;
	}
}
