package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.domain.RefsetMemberIntent;
import com.snomed.simplextoolkit.exceptions.ServiceException;

import java.util.List;

public interface SubsetUploadProvider {

	List<RefsetMemberIntent> readUpload() throws ServiceException;

}
