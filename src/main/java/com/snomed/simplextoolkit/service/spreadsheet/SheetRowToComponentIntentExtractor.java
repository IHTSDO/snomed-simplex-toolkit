package com.snomed.simplextoolkit.service.spreadsheet;

import com.snomed.simplextoolkit.domain.ComponentIntent;
import com.snomed.simplextoolkit.domain.RefsetMemberIntent;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.apache.poi.ss.usermodel.Row;

@FunctionalInterface
public interface SheetRowToComponentIntentExtractor<T extends ComponentIntent> {

	T extract(Row row, Integer rowNumber, HeaderConfiguration headerConfiguration) throws ServiceException;

}
