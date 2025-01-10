package org.snomed.simplex.util;

import org.slf4j.LoggerFactory;
import org.snomed.simplex.weblate.WeblateService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileUtils {
	public static void deleteOrLogWarning(File tempFile) {
		if (tempFile != null && tempFile.exists()) {
			try {
				Files.delete(tempFile.toPath());
			} catch (IOException e) {
				LoggerFactory.getLogger(WeblateService.class).warn("Failed to delete temp file {}", tempFile.getAbsoluteFile(), e);
			}
		}
	}
}
