package org.snomed.simplex.rest;

public class ControllerHelper {
	public static String normaliseFilename(String term) {
		return term.replace(" ", "_");
	}
}
