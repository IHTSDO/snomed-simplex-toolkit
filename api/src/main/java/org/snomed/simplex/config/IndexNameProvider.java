package org.snomed.simplex.config;

public class IndexNameProvider {
	private final String prefix;

	public IndexNameProvider(String indexPrefix) {
		this.prefix = indexPrefix;
	}

	public String indexName(String indexName) {
		return this.prefix != null && !this.prefix.isEmpty() ? this.prefix + indexName : indexName;
	}
}
