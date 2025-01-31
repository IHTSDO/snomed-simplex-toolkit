package org.snomed.simplex.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;

public class ServiceHelper {

	private ServiceHelper() {
	}

	public static void requiredParameter(String paramName, Object paramValue) throws ServiceExceptionWithStatusCode {
		if (paramValue == null) {
			throw new ServiceExceptionWithStatusCode("Parameter '%s' is required.".formatted(paramName), HttpStatus.BAD_REQUEST);
		}
	}

}
