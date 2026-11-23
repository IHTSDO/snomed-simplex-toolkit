package org.snomed.simplex.standardcodelist;

/** One value set entry within a standard code list catalogue's metadata.json. */
public class StandardCodeListValueSetMetadata {

	private String name;
	private String valueSetId;
	private String canonicalUrl;
	private String description;
	private String codeSystem;
	private String codeSystemVersion;
	private int conceptCount;
	private String file;
	private boolean partialExtract;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValueSetId() {
		return valueSetId;
	}

	public void setValueSetId(String valueSetId) {
		this.valueSetId = valueSetId;
	}

	public String getCanonicalUrl() {
		return canonicalUrl;
	}

	public void setCanonicalUrl(String canonicalUrl) {
		this.canonicalUrl = canonicalUrl;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public void setCodeSystem(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public String getCodeSystemVersion() {
		return codeSystemVersion;
	}

	public void setCodeSystemVersion(String codeSystemVersion) {
		this.codeSystemVersion = codeSystemVersion;
	}

	public int getConceptCount() {
		return conceptCount;
	}

	public void setConceptCount(int conceptCount) {
		this.conceptCount = conceptCount;
	}

	public String getFile() {
		return file;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public boolean isPartialExtract() {
		return partialExtract;
	}

	public void setPartialExtract(boolean partialExtract) {
		this.partialExtract = partialExtract;
	}
}
