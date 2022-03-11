package com.snomed.simpleextensiontoolkit.client;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ConceptMini {

	private String conceptId;
	private boolean active;
	private DescriptionMini fsn;
	private DescriptionMini pt;
	private String moduleId;
	private Long activeMemberCount;

	@JsonIgnore
	public String getPtOrFsnOrConceptId() {
		if (pt != null) {
			return pt.getTerm();
		} else if (fsn != null) {
			return fsn.getTerm();
		}
		return conceptId;
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

	public static final class DescriptionMini {

		private String term;
		private String lang;

		public String getTerm() {
			return term;
		}

		public String getLang() {
			return lang;
		}
	}
}
