package org.snomed.simplex.service.job;

import org.snomed.simplex.exceptions.ServiceException;

public class DummyChangeMonitor implements ChangeMonitor {
	@Override
	public void added(String conceptId, String summary) throws ServiceException {
		// Intentionally does nothing: changes are not tracked.
	}

	@Override
	public void removed(String conceptId, String summary) throws ServiceException {
		// Intentionally does nothing: changes are not tracked.
	}

	@Override
	public void updated(String conceptId, String summary) throws ServiceException {
		// Intentionally does nothing: changes are not tracked.
	}

	@Override
	public void noChange(String conceptId, String summary) throws ServiceException {
		// Intentionally does nothing: changes are not tracked.
	}
}
