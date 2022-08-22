package com.snomed.simplextoolkit.rest;

public class ControllerHelper {
	public static String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}
}
