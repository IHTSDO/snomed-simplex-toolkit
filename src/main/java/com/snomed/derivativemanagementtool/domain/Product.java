package com.snomed.derivativemanagementtool.domain;

import java.io.File;

public class Product {

	private final String name;
	private final String codesystem;
	private final String module;
	private final String refsetId;
	private final File productDir;

	public Product(File productDir, String name, String codesystem, String module, String refsetId) {
		this.productDir = productDir;
		this.name = name;
		this.codesystem = codesystem;
		this.module = module;
		this.refsetId = refsetId;
	}

	public File getProductDir() {
		return productDir;
	}

	public String getName() {
		return name;
	}

	public String getCodesystem() {
		return codesystem;
	}

	public String getModule() {
		return module;
	}

	public String getRefsetId() {
		return refsetId;
	}

	@Override
	public String toString() {
		return "Product{" +
				"name='" + name + '\'' +
				", codesystem='" + codesystem + '\'' +
				", module='" + module + '\'' +
				", refsetId='" + refsetId + '\'' +
				'}';
	}
}
