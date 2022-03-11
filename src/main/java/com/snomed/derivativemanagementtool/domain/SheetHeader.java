package com.snomed.derivativemanagementtool.domain;

public class SheetHeader {

	private final String name;
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
}
