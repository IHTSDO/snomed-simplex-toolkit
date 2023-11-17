package com.snomed.simplextoolkit.service.job;

import com.snomed.simplextoolkit.exceptions.ServiceException;

public interface AsyncFunction<T extends AsyncJob> {

	ChangeSummary run(T asyncJob) throws ServiceException;

}
