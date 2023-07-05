package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.domain.Description;
import com.snomed.simplextoolkit.exceptions.ServiceException;

import java.util.List;
import java.util.Map;

public interface TranslationUploadProvider {

	Map<Long, List<Description>> readUpload() throws ServiceException;

}
