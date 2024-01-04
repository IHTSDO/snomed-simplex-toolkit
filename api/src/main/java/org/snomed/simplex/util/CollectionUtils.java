package org.snomed.simplex.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class CollectionUtils {

	public static <T> Collection<T> orEmpty(Collection<T> collection) {
		return collection != null ? collection : Collections.emptyList();
	}

	public static <T> Collection<T> orEmpty(T[] array) {
		return array != null ? Arrays.asList(array) : Collections.emptyList();
	}

}
