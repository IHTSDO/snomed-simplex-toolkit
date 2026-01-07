package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.CodeSystem;

import java.util.ArrayList;
import java.util.List;

public class SnowstormExportConfiguration {
	private final SnowstormClient.ExportType exportType;
	private final CodeSystem codeSystem;
	private String transientEffectiveTime;
	private List<String> moduleIds;
	private boolean languageOnly;

	public SnowstormExportConfiguration(SnowstormClient.ExportType exportType, CodeSystem codeSystem) {
		this.exportType = exportType;
		this.codeSystem = codeSystem;
	}

	public SnowstormExportConfiguration setTransientEffectiveTime(String transientEffectiveTime) {
		this.transientEffectiveTime = transientEffectiveTime;
		return this;
	}

	public SnowstormExportConfiguration addModuleId(String moduleId) {
		if (moduleIds == null) {
			moduleIds = new ArrayList<>();
		}
		moduleIds.add(moduleId);
		return this;
	}

	public SnowstormExportConfiguration setLanguageOnly(boolean languageOnly) {
		this.languageOnly = languageOnly;
		return this;
	}

	public SnowstormClient.ExportType getExportType() {
		return exportType;
	}

	public CodeSystem getCodeSystem() {
		return codeSystem;
	}

	public String getTransientEffectiveTime() {
		return transientEffectiveTime;
	}

	public List<String> getModuleIds() {
		return moduleIds;
	}

	public boolean isLanguageOnly() {
		return languageOnly;
	}
}
