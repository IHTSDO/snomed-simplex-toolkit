package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BrowserDescriptionSearchResult {

	private String term;
	private boolean active;
	private String languageCode;
	private String module;
	private ConceptMini concept;

	public String getTerm() {
		return term;
	}

	public boolean isActive() {
		return active;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public String getModule() {
		return module;
	}

	public ConceptMini getConcept() {
		return concept;
	}
}
