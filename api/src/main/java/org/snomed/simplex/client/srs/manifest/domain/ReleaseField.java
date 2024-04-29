package org.snomed.simplex.client.srs.manifest.domain;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class ReleaseField {

	@JacksonXmlProperty(isAttribute = true)
	private final String name;

	public ReleaseField(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
