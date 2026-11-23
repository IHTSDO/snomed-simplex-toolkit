package org.snomed.simplex.standardcodelist;

import java.util.ArrayList;
import java.util.List;

/** Root of a standard code list catalogue's metadata.json (e.g. store/standard-code-lists/EHDS_MVC_10.0.0/metadata.json). */
public class StandardCodeListMetadata {

	private String id;
	private String name;
	private String sourceValueSetCatalogUid;
	private String sourceValueSetCatalogName;
	private String sourceValueSetCatalogDescription;
	private String sourceFile;
	private String codeSystem;
	private String notes;
	private String valueSetNamePrefix;
	private List<StandardCodeListValueSetMetadata> valueSets = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSourceValueSetCatalogUid() {
		return sourceValueSetCatalogUid;
	}

	public void setSourceValueSetCatalogUid(String sourceValueSetCatalogUid) {
		this.sourceValueSetCatalogUid = sourceValueSetCatalogUid;
	}

	public String getSourceValueSetCatalogName() {
		return sourceValueSetCatalogName;
	}

	public void setSourceValueSetCatalogName(String sourceValueSetCatalogName) {
		this.sourceValueSetCatalogName = sourceValueSetCatalogName;
	}

	public String getSourceValueSetCatalogDescription() {
		return sourceValueSetCatalogDescription;
	}

	public void setSourceValueSetCatalogDescription(String sourceValueSetCatalogDescription) {
		this.sourceValueSetCatalogDescription = sourceValueSetCatalogDescription;
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public void setSourceFile(String sourceFile) {
		this.sourceFile = sourceFile;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public void setCodeSystem(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getValueSetNamePrefix() {
		return valueSetNamePrefix;
	}

	public void setValueSetNamePrefix(String valueSetNamePrefix) {
		this.valueSetNamePrefix = valueSetNamePrefix;
	}

	public List<StandardCodeListValueSetMetadata> getValueSets() {
		return valueSets;
	}

	public void setValueSets(List<StandardCodeListValueSetMetadata> valueSets) {
		this.valueSets = valueSets;
	}
}
