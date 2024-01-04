package org.snomed.simplex.service;

import org.snomed.simplex.domain.RefsetMemberIntent;
import org.snomed.simplex.exceptions.ServiceException;

import java.util.List;

public interface SubsetUploadProvider {

	List<RefsetMemberIntent> readUpload() throws ServiceException;

}
