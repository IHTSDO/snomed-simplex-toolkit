package com.snomed.simplextoolkit.service.spreadsheet;

public class SheetHeader {

	private final String name;
	private String subtitle;
	private boolean optional;

	public SheetHeader(String name) {
		this.name = name;
	}

	public SheetHeader optional() {
		optional = true;
		return this;
	}

	public String getName() {
		return name;
	}

	public boolean isOptional() {
		return optional;
	}

	public SheetHeader setSubtitle(String subtitle) {
		this.subtitle = subtitle;
		return this;
	}

	public String getSubtitle() {
		return subtitle;
	}
}
