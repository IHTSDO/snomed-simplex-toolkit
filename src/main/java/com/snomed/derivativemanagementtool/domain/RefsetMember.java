package com.snomed.derivativemanagementtool.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class RefsetMember {

	private String memberId;
	private String effectiveTime;
	private boolean active;
	private String moduleId;
	private String refsetId;
	private String referencedComponentId;
	private Map<String, String> additionalFields;
	private boolean released;

	public RefsetMember() {
		// Used by object mapper
	}

	public RefsetMember(String refsetId, String moduleId, String referencedComponentId) {
		this.memberId = UUID.randomUUID().toString();
		this.active = true;
		this.refsetId = refsetId;
		this.moduleId = moduleId;
		this.referencedComponentId = referencedComponentId;
		additionalFields = new HashMap<>();
	}

	public String getMemberId() {
		return memberId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public Map<String, String> getAdditionalFields() {
		return additionalFields;
	}

	public boolean isReleased() {
		return released;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		RefsetMember that = (RefsetMember) o;
		return memberId.equals(that.memberId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberId);
	}

}
