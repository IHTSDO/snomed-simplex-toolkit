package org.snomed.simplex.service;

import org.snomed.simplex.exceptions.ServiceException;

import java.util.concurrent.Callable;

public interface ServiceCallable<V> extends Callable<V> {

	@Override
	V call() throws ServiceException;
}
