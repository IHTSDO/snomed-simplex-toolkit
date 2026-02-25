package org.snomed.simplex.service.job;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;

public interface APTaskCreationCallable {

	String createTaskBranch() throws ServiceExceptionWithStatusCode;

}
