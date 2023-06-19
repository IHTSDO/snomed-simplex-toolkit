package com.snomed.simplextoolkit.util;

import java.util.Collection;
import java.util.Collections;

public class CollectionUtils {

	public static <T> Collection<T> orEmpty(Collection<T> collection) {
		return collection != null ? collection : Collections.emptyList();
	}

}
