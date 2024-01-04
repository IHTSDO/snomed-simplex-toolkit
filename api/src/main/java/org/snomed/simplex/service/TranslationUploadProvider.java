package org.snomed.simplex.service;

import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.exceptions.ServiceException;

import java.util.List;
import java.util.Map;

public interface TranslationUploadProvider {

	Map<Long, List<Description>> readUpload() throws ServiceException;

}
