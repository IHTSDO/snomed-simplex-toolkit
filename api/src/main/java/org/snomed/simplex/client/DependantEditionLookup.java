package org.snomed.simplex.client;

import org.snomed.simplex.client.domain.CodeSystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

class DependantEditionLookup {

	record DependantEdition(String name, String shortName) {}

	private final Map<String, DependantEdition> cache = new ConcurrentHashMap<>();

	DependantEdition resolve(String parentBranch, Function<String, DependantEdition> fetcher) {
		DependantEdition cached = cache.get(parentBranch);
		if (cached != null) {
			return cached;
		}
		DependantEdition fetched = fetcher.apply(parentBranch);
		if (fetched != null) {
			cache.put(parentBranch, fetched);
		}
		return fetched;
	}

	void clear() {
		cache.clear();
	}

	static String getParentBranchPath(String branchPath) {
		if (branchPath == null) {
			return null;
		}
		int lastSlash = branchPath.lastIndexOf('/');
		if (lastSlash <= 0) {
			return null;
		}
		return branchPath.substring(0, lastSlash);
	}

	static void applyDependantEdition(CodeSystem codeSystem, DependantEdition dependantEdition) {
		if (dependantEdition == null) {
			return;
		}
		codeSystem.setDependantEditionName(dependantEdition.name());
		codeSystem.setDependantEditionShortName(dependantEdition.shortName());
	}
}
