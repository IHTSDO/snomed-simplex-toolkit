package org.snomed.simplex.service;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.UUID;

public class StreamUtils {

	private static final Logger logger = LoggerFactory.getLogger(StreamUtils.class);

	public static void copyViaTempFile(InputStream input, OutputStream output, boolean closeOutputStream) throws IOException {
		File tempFile = File.createTempFile("download" + UUID.randomUUID(), "tmp");
		try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
			Streams.copy(input, outputStream, true);
		}
		try (FileInputStream inputStream = new FileInputStream(tempFile)) {
			Streams.copy(inputStream, output, closeOutputStream);
		} finally {
			if (!tempFile.delete()) {
				logger.warn("Failed to delete temporary file {}", tempFile.getAbsolutePath());
			}
		}
	}

}
