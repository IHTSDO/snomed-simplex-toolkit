package org.snomed.simplex.util;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileUtils {

	private FileUtils() {}

	public static void deleteOrLogWarning(File tempFile) {
		if (tempFile != null && tempFile.exists()) {
			try {
				Files.delete(tempFile.toPath());
			} catch (IOException e) {
				LoggerFactory.getLogger(FileUtils.class).warn("Failed to delete temp file {}", tempFile.getAbsoluteFile(), e);
			}
		}
	}

	// Remove the UTF-8 Byte Order Mark
	public static String removeUTF8BOM(String line) {
		if (line == null) {
			return null;
		}
		final String UTF8_BOM = "\uFEFF";
		if (line.startsWith(UTF8_BOM)) {
			line = line.substring(1);
		}
		return line;
	}
}
