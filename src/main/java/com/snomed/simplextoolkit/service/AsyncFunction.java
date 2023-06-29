package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.domain.AsyncJob;
import com.snomed.simplextoolkit.exceptions.ServiceException;

public interface AsyncFunction {

	ChangeSummary run(AsyncJob asyncJob) throws ServiceException;

}
