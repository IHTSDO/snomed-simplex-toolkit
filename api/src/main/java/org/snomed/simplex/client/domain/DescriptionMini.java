package org.snomed.simplex.client.domain;

public final class DescriptionMini {

	private String term;
	private String lang;

	public DescriptionMini() {
	}

	public DescriptionMini(String term, String lang) {
		this.term = term;
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public String getLang() {
		return lang;
	}
}
