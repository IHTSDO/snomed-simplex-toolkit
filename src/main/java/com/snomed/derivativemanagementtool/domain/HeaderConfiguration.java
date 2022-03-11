package com.snomed.derivativemanagementtool.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the headers found in an uploaded spreadsheet.
 * Allows for columns to be presented in a different order, for header names to be matched ignoring case and for some columns to be optional.
 */
public class HeaderConfiguration {

	private final Map<SheetHeader, Integer> headerColumnMap;

	public HeaderConfiguration() {
		headerColumnMap = new HashMap<>();
	}

	public void addHeader(SheetHeader header, int column) {
		headerColumnMap.put(header, column);
	}

	public Integer getColumn(String headerName) {
		for (Map.Entry<SheetHeader, Integer> entry : headerColumnMap.entrySet()) {
			if (entry.getKey().getName().equalsIgnoreCase(headerName)) {
				return entry.getValue();
			}
		}
		return null;
	}
}
