package org.snomed.simplex.rest.pojos;

public class CreateCodeSystemRequest {

	private String name;
	private String shortName;
	private String namespace;
	private boolean createModule;
	private String moduleName;
	private String moduleId;
	private String dependantCodeSystem;
	private Integer dependantCodeSystemVersion;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getShortName() {
		return shortName;
	}

	public CreateCodeSystemRequest setShortName(String shortName) {
		this.shortName = shortName;
		return this;
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

	public String getDependantCodeSystem() {
		return dependantCodeSystem;
	}

	public void setDependantCodeSystem(String dependantCodeSystem) {
		this.dependantCodeSystem = dependantCodeSystem;
	}

	public Integer getDependantCodeSystemVersion() {
		return dependantCodeSystemVersion;
	}

	public void setDependantCodeSystemVersion(Integer dependantCodeSystemVersion) {
		this.dependantCodeSystemVersion = dependantCodeSystemVersion;
	}
}
