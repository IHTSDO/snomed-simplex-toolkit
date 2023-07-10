package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.exceptions.ServiceException;

public interface ChangeMonitor {

	void added(String conceptId, String summary) throws ServiceException;

	void removed(String conceptId, String summary) throws ServiceException;

	void updated(String conceptId, String summary) throws ServiceException;

	void noChange(String conceptId, String summary) throws ServiceException;

}
