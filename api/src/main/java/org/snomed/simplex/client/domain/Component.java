package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class Component {

	private boolean active;
	private boolean released;
	private String moduleId;
	private String effectiveTime;

	protected Component() {
		active = true;
	}

	protected Component(String moduleId) {
		this();
		this.moduleId = moduleId;
	}

	@JsonIgnore
	public abstract String getId();

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isReleased() {
		return released;
	}

	public Description setReleased(boolean released) {
		this.released = released;
		return null;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}
}
