package com.snomed.simplextoolkit.domain;

import java.util.List;

public class Page<T> {

	private List<T> items;
	private Integer total;
	private Integer offset;
	private String searchAfter;

	public Page() {
	}

	public Page(List<T> allItems) {
		offset = 0;
		total = allItems.size();
		this.items = allItems;
	}

	public List<T> getItems() {
		return items;
	}

	public Integer getTotal() {
		return total;
	}

	public Integer getOffset() {
		return offset;
	}

	public String getSearchAfter() {
		return searchAfter;
	}
}
