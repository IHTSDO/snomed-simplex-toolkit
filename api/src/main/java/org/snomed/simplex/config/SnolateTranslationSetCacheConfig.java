package org.snomed.simplex.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class SnolateTranslationSetCacheConfig {

	/**
	 * Cache name for {@link org.snomed.simplex.snolate.sets.SnolateSetRefsetCache}: lists of translation sets
	 * per code system and language refset (Elasticsearch query used by {@code findSubsetOrThrow}).
	 */
	public static final String SNOLATE_SETS_BY_CODE_SYSTEM_AND_REFSET = "snolateSetsByCodeSystemAndRefset";
}
