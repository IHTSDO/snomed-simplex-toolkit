package com.snomed.derivativemanagementtool.client.domain;

import java.util.Map;

public class Description {

	private String moduleId;
	private String typeId;
	private String lang;
	private String term;
	private String caseSignificance;
	private Map<String, String> acceptabilityMap;

	public Description() {
	}

	public Description(String typeId, String lang, String term, String caseSignificance, String langRefset, String acceptability) {
		this(typeId, lang, term, caseSignificance, Map.of(langRefset, acceptability));
	}

	public Description(String typeId, String lang, String term, String caseSignificance, Map<String, String> acceptabilityMap) {
		this.typeId = typeId;
		this.lang = lang;
		this.term = term;
		this.caseSignificance = caseSignificance;
		this.acceptabilityMap = acceptabilityMap;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getTypeId() {
		return typeId;
	}

	public void setTypeId(String typeId) {
		this.typeId = typeId;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getCaseSignificance() {
		return caseSignificance;
	}

	public void setCaseSignificance(String caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public Map<String, String> getAcceptabilityMap() {
		return acceptabilityMap;
	}

	public void setAcceptabilityMap(Map<String, String> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
	}
}
