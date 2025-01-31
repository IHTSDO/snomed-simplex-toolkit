package org.snomed.simplex.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SupplierUtil {

	private SupplierUtil() {
	}

	public static <T> List<T> getBatch(int i, Supplier<T> stream) {
		List<T> batch = new ArrayList<>();
		while (batch.size() < i) {
			T t = stream.get();
			if (t != null) {
				batch.add(t);
			} else {
				break;
			}
		}
		return batch;
	}

}
