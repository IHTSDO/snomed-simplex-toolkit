package com.snomed.simpleextensiontoolkit.service;

import org.apache.tomcat.util.http.fileupload.util.Streams;

import java.io.*;

public class StreamUtils {
	public static void copyViaTempFile(InputStream input, OutputStream output, boolean closeOutputStream) throws IOException {
		File tempFile = File.createTempFile("download", "tmp");
		Streams.copy(input, new FileOutputStream(tempFile), true);
		Streams.copy(new FileInputStream(tempFile), output, closeOutputStream);
	}
}
