package org.snomed.simplex.rest.pojos;

public class CreateCodeSystemRequest {

	private String name;
	private String shortName;
	private String namespace;
	private boolean createModule;
	private String moduleName;
	private String moduleId;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getModuleName() {
		return moduleName;
	}

	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	public boolean isCreateModule() {
		return createModule;
	}

	public String getModuleId() {
		return moduleId;
	}
}
