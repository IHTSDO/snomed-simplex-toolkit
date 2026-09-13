package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SnowstormClientCacheRefreshTest {

	private static final String SHORT_NAME = "SNOMEDCT-TEST";
	private static final String BRANCH_PATH = "MAIN/SNOMEDCT-TEST";

	private SnowstormClient snowstormClient;

	@BeforeEach
	void setUp() {
		snowstormClient = spy(new SnowstormClient("http://localhost:8080", null, "Simplex test", new ObjectMapper(), 10));
	}

	@Test
	void getCodeSystemOrThrow_cacheHit_refreshesContentHeadTimestamp() throws Exception {
		CodeSystem cachedCodeSystem = cachedCodeSystem(100L, true);
		seedCodeSystemCache(cachedCodeSystem);

		Branch branch = branchWithHead(200L, "true");
		doReturn(branch).when(snowstormClient).getBranchOrThrow(BRANCH_PATH);

		CodeSystem result = snowstormClient.getCodeSystemOrThrow(SHORT_NAME);

		assertEquals(200L, result.getContentHeadTimestamp());
	}

	@Test
	void getCodeSystemOrThrow_cacheHit_refreshesClassifiedFlag() throws Exception {
		CodeSystem cachedCodeSystem = cachedCodeSystem(100L, true);
		seedCodeSystemCache(cachedCodeSystem);

		Branch branch = branchWithHead(100L, "false");
		doReturn(branch).when(snowstormClient).getBranchOrThrow(BRANCH_PATH);

		CodeSystem result = snowstormClient.getCodeSystemOrThrow(SHORT_NAME);

		assertFalse(result.isClassified());
	}

	private CodeSystem cachedCodeSystem(long contentHeadTimestamp, boolean classified) {
		CodeSystem codeSystem = new CodeSystem("Test", SHORT_NAME, BRANCH_PATH);
		codeSystem.setContentHeadTimestamp(contentHeadTimestamp);
		codeSystem.setClassified(classified);
		return codeSystem;
	}

	private Branch branchWithHead(long headTimestamp, String classified) {
		Branch branch = mock(Branch.class);
		when(branch.getHeadTimestamp()).thenReturn(headTimestamp);
		when(branch.getMetadataValue(Branch.CLASSIFIED_METADATA_KEY)).thenReturn(classified);
		return branch;
	}

	private void seedCodeSystemCache(CodeSystem codeSystem) throws Exception {
		Field cacheField = SnowstormClient.class.getDeclaredField("codeSystemCache");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> cache = (Map<String, Object>) cacheField.get(snowstormClient);

		Class<?> cachedClass = Class.forName("org.snomed.simplex.client.SnowstormClient$CachedCodeSystem");
		Constructor<?> constructor = cachedClass.getDeclaredConstructor(CodeSystem.class, long.class);
		constructor.setAccessible(true);
		Object cached = constructor.newInstance(codeSystem, System.currentTimeMillis());
		cache.put(codeSystem.getShortName(), cached);
	}

}
