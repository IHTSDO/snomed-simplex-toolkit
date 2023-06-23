package com.snomed.simplextoolkit.client.domain;

import java.util.Map;

public class Description extends Component {

	private String descriptionId;
	private String term;
	private String conceptId;
	private String type;
	private String lang;
	private String caseSignificance;
	private Map<String, String> acceptabilityMap;
	private String inactivationIndicator;

	public Description() {
	}

	public Description(String type, String lang, String term, String caseSignificance, String langRefset, String acceptability) {
		this(type, lang, term, caseSignificance, Map.of(langRefset, acceptability));
	}

	public Description(String type, String lang, String term, String caseSignificance, Map<String, String> acceptabilityMap) {
		this.type = type;
		this.lang = lang;
		this.term = term;
		this.caseSignificance = caseSignificance;
		this.acceptabilityMap = acceptabilityMap;
	}

	public String getDescriptionId() {
		return descriptionId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
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

	public String getInactivationIndicator() {
		return inactivationIndicator;
	}

	@Override
	public String toString() {
		return "Description{" +
				"lang='" + lang + '\'' +
				", term='" + term + '\'' +
				", caseSignificance='" + caseSignificance + '\'' +
				", acceptabilityMap=" + acceptabilityMap +
				'}';
	}
}
