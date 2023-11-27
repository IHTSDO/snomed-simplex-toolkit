package com.snomed.simplextoolkit.service.job;

import com.snomed.simplextoolkit.exceptions.ServiceException;

public class DummyChangeMonitor implements ChangeMonitor {
	@Override
	public void added(String conceptId, String summary) throws ServiceException {

	}

	@Override
	public void removed(String conceptId, String summary) throws ServiceException {

	}

	@Override
	public void updated(String conceptId, String summary) throws ServiceException {

	}

	@Override
	public void noChange(String conceptId, String summary) throws ServiceException {

	}
}
