package org.snomed.simplex.service.spreadsheet;

import org.snomed.simplex.domain.ComponentIntent;
import org.snomed.simplex.exceptions.ServiceException;
import org.apache.poi.ss.usermodel.Row;

@FunctionalInterface
public interface SheetRowToComponentIntentExtractor<T extends ComponentIntent> {

	T extract(Row row, Integer rowNumber, HeaderConfiguration headerConfiguration) throws ServiceException;

}
