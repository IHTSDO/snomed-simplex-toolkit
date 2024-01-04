package org.snomed.simplex.service.job;

import org.snomed.simplex.exceptions.ServiceException;

public interface ChangeMonitor {

	void added(String conceptId, String summary) throws ServiceException;

	void removed(String conceptId, String summary) throws ServiceException;

	void updated(String conceptId, String summary) throws ServiceException;

	void noChange(String conceptId, String summary) throws ServiceException;

}
