package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SnowstormContentPage<T> {

	private List<T> items;
	private long totalElements;

	public List<T> getItems() {
		return items != null ? items : Collections.emptyList();
	}

	public long getTotalElements() {
		return totalElements;
	}
}
