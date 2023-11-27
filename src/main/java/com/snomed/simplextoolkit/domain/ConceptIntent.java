package com.snomed.simplextoolkit.domain;

import java.util.*;

public class ConceptIntent implements ComponentIntent {

	private int rowNumber;
	private String parentCode;
	private String conceptCode;
	private boolean inactive;
	private final Map<String, List<String>> langRefsetTerms;

	public ConceptIntent() {
		langRefsetTerms = new HashMap<>();
	}

	public ConceptIntent(String parentCode, int row) {
		this();
		this.parentCode = parentCode;
		this.rowNumber = row;
	}

	public ConceptIntent addTerm(String term, String langRefset) {
		langRefsetTerms.computeIfAbsent(langRefset, i -> new ArrayList<>()).add(term);
		return this;
	}

	public String getParentCode() {
		return parentCode;
	}

	public int getRowNumber() {
		return rowNumber;
	}

	public void setInactive(boolean inactive) {
		this.inactive = inactive;
	}

	public boolean isInactive() {
		return inactive;
	}

	public Map<String, List<String>> getLangRefsetTerms() {
		return langRefsetTerms;
	}

	public void setConceptCode(String conceptCode) {
		this.conceptCode = conceptCode;
	}

	public String getConceptCode() {
		return conceptCode;
	}
}
