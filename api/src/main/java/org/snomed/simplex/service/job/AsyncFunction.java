package org.snomed.simplex.service.job;

import org.snomed.simplex.exceptions.ServiceException;

public interface AsyncFunction<T extends AsyncJob> {

	ChangeSummary run(T asyncJob) throws ServiceException;

}
