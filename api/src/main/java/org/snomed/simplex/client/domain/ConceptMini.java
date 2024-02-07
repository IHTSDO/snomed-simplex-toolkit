package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;

public class ConceptMini {

	private String conceptId;
	private boolean active;
	private DescriptionMini fsn;
	private DescriptionMini pt;
	private String moduleId;
	private Long activeMemberCount;
	private Map<String, Object> extraFields;

	@JsonIgnore
	public String getPtOrFsnOrConceptId() {
		if (pt != null && pt.getTerm() != null) {
			return pt.getTerm();
		} else if (fsn != null && fsn.getTerm() != null) {
			return fsn.getTerm();
		}
		return conceptId;
	}

	public void addExtraField(String name, Object value) {
		if (extraFields == null) {
			extraFields = new HashMap<>();
		}
		extraFields.put(name, value);
	}

	public String getConceptId() {
		return conceptId;
	}

	public boolean isActive() {
		return active;
	}

	public DescriptionMini getFsn() {
		return fsn;
	}

	public DescriptionMini getPt() {
		return pt;
	}

	public String getModuleId() {
		return moduleId;
	}

	public Long getActiveMemberCount() {
		return activeMemberCount;
	}

	public void setActiveMemberCount(Long activeMemberCount) {
		this.activeMemberCount = activeMemberCount;
	}

	public String getIdAndFsnTerm() {
		DescriptionMini fsn = getFsn();
		return String.format("%s |%s|", conceptId, fsn != null ? fsn.getTerm() : "");
	}

	@JsonAnyGetter
	public Map<String, Object> getExtraFields() {
		return extraFields;
	}

	public void setExtraFields(Map<String, Object> extraFields) {
		this.extraFields = extraFields;
	}

}
