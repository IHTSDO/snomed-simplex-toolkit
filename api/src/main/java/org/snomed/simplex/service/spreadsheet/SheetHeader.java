package org.snomed.simplex.service.spreadsheet;

import java.util.Objects;

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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SheetHeader that = (SheetHeader) o;
		return Objects.equals(name, that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}
}
