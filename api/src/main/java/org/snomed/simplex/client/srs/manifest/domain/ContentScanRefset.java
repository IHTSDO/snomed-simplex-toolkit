package org.snomed.simplex.client.srs.manifest.domain;

import java.util.List;

public class ContentScanRefset {

	private String usEnglishPT;
	private String refset;
	private List<String> additionalFields;

	public String getUsEnglishPT() {
		return usEnglishPT;
	}

	public void setUsEnglishPT(String usEnglishPT) {
		this.usEnglishPT = usEnglishPT;
	}

	public String getRefset() {
		return refset;
	}

	public void setRefset(String refset) {
		this.refset = refset;
	}

	public List<String> getAdditionalFields() {
		return additionalFields;
	}

	public void setAdditionalFields(List<String> additionalFields) {
		this.additionalFields = additionalFields;
	}
}
