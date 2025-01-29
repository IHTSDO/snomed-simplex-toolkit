package org.snomed.simplex.weblate;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.WeblateUnit;

import java.util.ArrayList;
import java.util.List;

public interface UnitSupplier {

	WeblateUnit get() throws ServiceExceptionWithStatusCode;

	default List<WeblateUnit> getBatch(int i) throws ServiceExceptionWithStatusCode {
		List<WeblateUnit> batch = new ArrayList<>();
		for (int j = 0; j < i; j++) {
			WeblateUnit unit = get();
			if (unit == null) {
				break;
			}
			batch.add(unit);
		}
		return batch;
	}
}
