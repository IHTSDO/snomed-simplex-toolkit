package org.snomed.simplex.service;

import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.job.ChangeMonitor;

import java.io.*;

public class CSVOutputChangeMonitor implements ChangeMonitor, AutoCloseable {

	private final BufferedWriter writer;

	public CSVOutputChangeMonitor(OutputStream outputStream) {
		this.writer = new BufferedWriter(new OutputStreamWriter(outputStream));

	}

	@Override
	public void added(String conceptId, String summary) throws ServiceException {
		writeLine("add", conceptId, summary);
	}

	@Override
	public void removed(String conceptId, String summary) throws ServiceException {
		writeLine("remove", conceptId, summary);
	}

	@Override
	public void updated(String conceptId, String summary) throws ServiceException {
		writeLine("updated", conceptId, summary);
	}

	@Override
	public void noChange(String conceptId, String summary) throws ServiceException {
		writeLine("no-change", conceptId, summary);
	}

	private void writeLine(String changeType, String conceptId, String summary) throws ServiceException {
		try {
			writer.write(changeType);
			writer.write("\t");
			writer.write(conceptId);
			writer.write("\t");
			writer.write(summary);
			writer.newLine();
		} catch (IOException e) {
			throw new ServiceException("Failed to write to change monitor file.", e);
		}
	}

	@Override
	public void close() throws ServiceException {
		try {
			writer.close();
		} catch (IOException e) {
			throw new ServiceException("Failed to close change monitor file.", e);
		}
	}
}
