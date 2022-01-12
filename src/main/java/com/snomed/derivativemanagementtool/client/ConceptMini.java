package com.snomed.derivativemanagementtool.client;

public class ConceptMini {

	private String conceptId;
	private boolean active;
	private DescriptionMini fsn;
	private DescriptionMini pt;
	private String moduleId;
	private Long activeMemberCount;

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
