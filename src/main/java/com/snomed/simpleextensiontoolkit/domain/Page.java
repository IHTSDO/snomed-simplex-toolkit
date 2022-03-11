package com.snomed.simpleextensiontoolkit.domain;

import java.util.List;

public class Page<T> {

	private List<T> items;
	private Long total;
	private Long offset;
	private String searchAfter;

	public List<T> getItems() {
		return items;
	}

	public Long getTotal() {
		return total;
	}

	public String getSearchAfter() {
		return searchAfter;
	}
}
