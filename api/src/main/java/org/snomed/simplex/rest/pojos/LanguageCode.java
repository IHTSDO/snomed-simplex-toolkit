package org.snomed.simplex.rest.pojos;

public class LanguageCode {

	private String name;
	private String code;

	public LanguageCode(String name, String code) {
		this.name = name;
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return code;
	}
}
